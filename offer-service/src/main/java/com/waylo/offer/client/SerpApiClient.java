package com.waylo.offer.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Тонкий клієнт до SerpAPI. Один HTTP-виклик = одна відповідь-рядок (сирий JSON);
 * розбір у наші DTO — на боці {@link FlightOfferMapper} / {@link HotelOfferMapper}.
 *
 * Кеш працює на рівні СИРОГО JSON: {@link String} серіалізується у Redis
 * стандартним серіалізатором без зайвих залежностей, і менеджер кешу Spring Boot
 * бере TTL із {@code spring.cache.redis.time-to-live}. Хіт кешу = пропускаємо
 * HTTP-виклик, DTO перепарсюємо на льоту (payload малий).
 */
@Component
public class SerpApiClient {

    private static final Logger log = LoggerFactory.getLogger(SerpApiClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String defaultCurrency;

    public SerpApiClient(
            RestClient.Builder builder,
            @Value("${app.providers.serpapi.base-url:https://serpapi.com}") String baseUrl,
            @Value("${app.providers.serpapi.api-key:}") String apiKey,
            @Value("${app.providers.serpapi.default-currency:EUR}") String defaultCurrency) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.defaultCurrency = defaultCurrency;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String defaultCurrency() {
        return defaultCurrency;
    }

    /**
     * Ключ кешу — стабільний хеш параметрів (LinkedHashMap тримає порядок).
     * {@code unless} фільтрує порожні/null відповіді, щоб не замикати помилки у кеші.
     */
    @Cacheable(cacheNames = "serpapi",
               key = "#engine + ':' + T(java.util.Objects).hash(#params)",
               unless = "#result == null || #result.isBlank()")
    public String search(String engine, Map<String, String> params) {
        if (!isEnabled()) {
            throw new IllegalStateException("SERPAPI_API_KEY не заданий");
        }
        Objects.requireNonNull(engine);
        Map<String, String> ordered = new LinkedHashMap<>(params);
        return restClient.get()
            .uri(b -> {
                b.path("/search.json")
                    .queryParam("engine", engine)
                    .queryParam("api_key", apiKey)
                    .queryParam("no_cache", "false");
                for (Map.Entry<String, String> e : ordered.entrySet()) {
                    if (e.getValue() != null && !e.getValue().isBlank()) {
                        b.queryParam(e.getKey(), e.getValue());
                    }
                }
                return b.build();
            })
            .retrieve()
            .body(String.class);
    }
}
