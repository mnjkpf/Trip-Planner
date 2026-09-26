package com.travelplanner.place.provider;

import com.travelplanner.place.domain.PlaceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Провайдер поверх Geoapify Places API.
 *   GET {base-url}/places?categories=...&filter=circle:{lon},{lat},{radius}&limit=...&apiKey=...
 * Відповідь (GeoJSON) розбирає GeoapifyMapper. Виклик деградує мʼяко: якщо ключа
 * немає або запит не вдався — повертаємо порожньо, і пошук віддасть те, що вже є в БД.
 */
@Component
public class GeoapifyPlacesProvider implements PlacesProvider {

    private static final Logger log = LoggerFactory.getLogger(GeoapifyPlacesProvider.class);
    private static final int LIMIT = 40;

    private final RestClient restClient;
    private final GeoapifyMapper mapper;
    private final String apiKey;

    public GeoapifyPlacesProvider(RestClient.Builder builder,
                                  GeoapifyMapper mapper,
                                  @Value("${app.providers.geoapify.base-url}") String baseUrl,
                                  @Value("${app.providers.geoapify.api-key:}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "geoapify";
    }

    @Override
    public List<PlaceCandidate> searchNearby(double lat, double lon, int radiusMeters, PlaceCategory category) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEOAPIFY_API_KEY не заданий — зовнішній пошук пропущено");
            return List.of();
        }

        String categories = mapper.toGeoapifyCategories(category);
        String filter = "circle:" + lon + "," + lat + "," + radiusMeters;   // Geoapify: lon,lat
        try {
            String body = restClient.get()
                    .uri(b -> b.path("/places")
                            .queryParam("categories", categories)
                            .queryParam("filter", filter)
                            .queryParam("limit", LIMIT)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            List<PlaceCandidate> candidates = mapper.parse(body);
            log.info("geoapify: {} кандидатів (lat={}, lon={}, r={}м, cat={})",
                    candidates.size(), lat, lon, radiusMeters, category);
            return candidates;
        } catch (Exception ex) {
            log.warn("geoapify виклик не вдався ({}) — повертаю порожньо", ex.getMessage());
            return List.of();
        }
    }
}
