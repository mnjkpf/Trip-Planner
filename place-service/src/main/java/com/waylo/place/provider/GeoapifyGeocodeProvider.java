package com.waylo.place.provider;

import com.waylo.place.dto.CitySuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Geoapify Geocoding Autocomplete:
 *   GET {geocode-base-url}/geocode/autocomplete?text=..&type=city&format=json&limit=..&apiKey=..
 * Ключ лишається на сервері — браузер його не бачить. Деградує мʼяко: без ключа
 * або при помилці повертаємо порожній список.
 */
@Component
public class GeoapifyGeocodeProvider implements GeocodeProvider {

    private static final Logger log = LoggerFactory.getLogger(GeoapifyGeocodeProvider.class);

    private final RestClient restClient;
    private final GeoapifyGeocodeMapper mapper;
    private final String apiKey;

    public GeoapifyGeocodeProvider(
            RestClient.Builder builder,
            GeoapifyGeocodeMapper mapper,
            @Value("${app.providers.geoapify.geocode-base-url:https://api.geoapify.com/v1}") String baseUrl,
            @Value("${app.providers.geoapify.api-key:}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<CitySuggestion> suggestCities(String query, int limit) {
        if (!isEnabled()) {
            log.warn("GEOAPIFY_API_KEY не заданий — пошук міста недоступний");
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String body = restClient.get()
                    .uri(b -> b.path("/geocode/autocomplete")
                            .queryParam("text", query)
                            .queryParam("type", "city")
                            .queryParam("format", "json")
                            .queryParam("limit", limit)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);
            List<CitySuggestion> cities = mapper.parseCities(body);
            log.info("geoapify geocode '{}' → {} міст", query, cities.size());
            return cities;
        } catch (Exception ex) {
            log.warn("geoapify geocode '{}' не вдався ({})", query, ex.getMessage());
            return List.of();
        }
    }
}
