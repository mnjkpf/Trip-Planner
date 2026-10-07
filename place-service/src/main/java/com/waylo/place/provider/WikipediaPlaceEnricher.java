package com.waylo.place.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;

/**
 * Збагачення фото через Wikipedia. Дві спроби, саме в такому порядку:
 *
 *   1. REST Summary API — GET {base-url}/page/summary/{назва}. Працює, коли назва
 *      місця збігається із заголовком статті («Colosseum», «Pantheon»).
 *   2. Пошук MediaWiki — /w/api.php?action=query&list=search&srsearch={назва} {місто}.
 *      Потрібен, бо статті часто звуться інакше: «National Botanic Gardens, Dublin».
 *      Знайдений заголовок перевіряємо на схожість і лише тоді беремо його summary.
 *
 * Ключ не потрібен, але Wikimedia вимагає змістовний User-Agent. Деградує мʼяко:
 * немає статті / вона неоднозначна / сервіс недоступний — повертаємо null.
 */
@Component
public class WikipediaPlaceEnricher implements PlaceEnricher {

    private static final Logger log = LoggerFactory.getLogger(WikipediaPlaceEnricher.class);

    private final RestClient restClient;
    private final RestClient searchClient;
    private final WikipediaSummaryMapper mapper;

    public WikipediaPlaceEnricher(
            RestClient.Builder builder,
            WikipediaSummaryMapper mapper,
            @Value("${app.providers.wikipedia.base-url:https://en.wikipedia.org/api/rest_v1}") String baseUrl,
            @Value("${app.providers.wikipedia.search-url:https://en.wikipedia.org/w}") String searchUrl,
            @Value("${app.providers.wikipedia.user-agent:Waylo/1.0 (portfolio pet project)}") String userAgent) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
        this.searchClient = builder
                .baseUrl(searchUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
        this.mapper = mapper;
    }

    @Override
    public boolean isEnabled() {
        return true;   // ключ не потрібен
    }

    @Override
    public PlaceImage fetchImage(String name, String city) {
        if (name == null || name.isBlank()) {
            return null;
        }
        PlaceImage direct = summary(name);
        if (direct != null && direct.imageUrl() != null) {
            log.info("wikipedia '{}' → фото знайдено за назвою", name);
            return direct;
        }

        String title = searchTitle(name, city);
        if (title != null) {
            PlaceImage viaSearch = summary(title);
            if (viaSearch != null && viaSearch.imageUrl() != null) {
                log.info("wikipedia '{}' → фото знайдено через пошук ('{}')", name, title);
                return viaSearch;
            }
        }
        log.info("wikipedia '{}' → без фото", name);
        return direct;      // може нести wikidataId, навіть якщо фото немає
    }

    private PlaceImage summary(String title) {
        try {
            String body = restClient.get()
                    .uri("/page/summary/{title}", title)
                    .retrieve()
                    .body(String.class);
            return mapper.parseSummaryImage(body);
        } catch (RestClientResponseException ex) {
            if (isRetryable(ex.getStatusCode().value())) {
                throw new EnrichmentUnavailableException("wikipedia " + ex.getStatusCode(), ex);
            }
            log.debug("wikipedia summary '{}': {}", title, ex.getStatusCode());
            return null;      // 404 і подібне — статті просто немає
        } catch (EnrichmentUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            // Мережа або таймаут — відповіді не було, тож запамʼятовувати нічого.
            throw new EnrichmentUnavailableException("wikipedia недоступна: " + ex.getMessage(), ex);
        }
    }

    /** 429 і 5xx минають самі; решту кодів вважаємо остаточною відповіддю. */
    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    /**
     * Заголовок статті через пошук. Назву міста додаємо лише в запит — як підказку;
     * у відповіді перевіряємо, що знайдене таки про наше місце, інакше пошук радо
     * підсуне першу-ліпшу статтю й кафе отримає чуже фото.
     */
    private String searchTitle(String name, String city) {
        String query = city == null || city.isBlank() ? name : name + " " + city;
        try {
            String body = searchClient.get()
                    .uri(b -> b.path("/api.php")
                            .queryParam("action", "query")
                            .queryParam("list", "search")
                            .queryParam("srsearch", query)
                            .queryParam("srlimit", 1)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class);
            String title = mapper.parseFirstSearchTitle(body);
            return isAbout(title, name) ? title : null;
        } catch (RestClientResponseException ex) {
            if (isRetryable(ex.getStatusCode().value())) {
                throw new EnrichmentUnavailableException("wikipedia пошук " + ex.getStatusCode(), ex);
            }
            return null;
        } catch (Exception ex) {
            throw new EnrichmentUnavailableException("wikipedia пошук недоступний: " + ex.getMessage(), ex);
        }
    }

    /** Заголовок має містити назву місця (або навпаки) — інакше це вже інша тема. */
    private static boolean isAbout(String title, String name) {
        if (title == null) {
            return false;
        }
        String t = title.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        return t.contains(n) || n.contains(t);
    }
}
