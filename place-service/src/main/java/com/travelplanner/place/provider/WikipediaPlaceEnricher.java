package com.travelplanner.place.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Збагачення фото через Wikipedia REST Summary API:
 *   GET {base-url}/page/summary/{title}
 * За назвою місця тягнемо головне зображення статті. Ключ не потрібен, але Wikimedia
 * вимагає змістовний User-Agent. Пробіли в назві кодуються автоматично. Деградує мʼяко:
 * немає статті / вона неоднозначна / сервіс недоступний — повертаємо null.
 */
@Component
public class WikipediaPlaceEnricher implements PlaceEnricher {

    private static final Logger log = LoggerFactory.getLogger(WikipediaPlaceEnricher.class);

    private final RestClient restClient;
    private final WikipediaSummaryMapper mapper;

    public WikipediaPlaceEnricher(
            RestClient.Builder builder,
            WikipediaSummaryMapper mapper,
            @Value("${app.providers.wikipedia.base-url:https://en.wikipedia.org/api/rest_v1}") String baseUrl,
            @Value("${app.providers.wikipedia.user-agent:TravelPlanner/1.0 (portfolio pet project)}") String userAgent) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
        this.mapper = mapper;
    }

    @Override
    public boolean isEnabled() {
        return true;   // ключ не потрібен
    }

    @Override
    public PlaceImage fetchImage(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            String body = restClient.get()
                    .uri("/page/summary/{title}", name)
                    .retrieve()
                    .body(String.class);
            PlaceImage img = mapper.parseSummaryImage(body);
            log.info("wikipedia summary '{}' → {}",
                    name, img != null && img.imageUrl() != null ? "фото знайдено" : "без фото");
            return img;
        } catch (Exception ex) {
            log.warn("wikipedia summary '{}' не вдалось ({}) — без фото", name, ex.getMessage());
            return null;
        }
    }
}
