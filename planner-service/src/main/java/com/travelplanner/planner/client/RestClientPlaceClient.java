package com.travelplanner.planner.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Виклик place-service через RestClient, обгорнутий circuit breaker'ом.
 * Якщо place-service падає (або коло відкрите) — мʼяка деградація: порожній список,
 * маршрут будується без місць. executeSupplier виконується на потоці виклику,
 * тож trace-контекст (traceparent) зберігається.
 */
@Component
public class RestClientPlaceClient implements PlaceClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientPlaceClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public RestClientPlaceClient(RestClient.Builder builder,
                                 CircuitBreakerRegistry registry,
                                 @Value("${app.services.place-uri}") String placeUri) {
        this.restClient = builder.baseUrl(placeUri).build();
        this.circuitBreaker = registry.circuitBreaker("place-service");
    }

    @Override
    public List<PlaceDto> searchNearby(double lat, double lon, int radiusMeters) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                List<PlaceDto> body = restClient.get()
                        .uri(b -> b.path("/api/places/search")
                                .queryParam("lat", lat)
                                .queryParam("lon", lon)
                                .queryParam("radius", radiusMeters)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<PlaceDto>>() {});
                return body == null ? List.<PlaceDto>of() : body;
            });
        } catch (Exception ex) {
            log.warn("place-service недоступний ({}) — маршрут без місць", ex.getMessage());
            return List.of();
        }
    }
}
