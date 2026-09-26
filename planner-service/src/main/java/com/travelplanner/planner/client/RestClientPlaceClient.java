package com.travelplanner.planner.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Реальний виклик place-service через RestClient. Базовий URL — з app.services.place-uri
 * (за замовчуванням http://localhost:8082; у compose/gateway це інша адреса).
 */
@Component
public class RestClientPlaceClient implements PlaceClient {

    private final RestClient restClient;

    public RestClientPlaceClient(RestClient.Builder builder,
                                 @Value("${app.services.place-uri}") String placeUri) {
        this.restClient = builder.baseUrl(placeUri).build();
    }

    @Override
    public List<PlaceDto> searchNearby(double lat, double lon, int radiusMeters) {
        List<PlaceDto> body = restClient.get()
                .uri(b -> b.path("/api/places/search")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("radius", radiusMeters)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<PlaceDto>>() {});
        return body == null ? List.of() : body;
    }
}
