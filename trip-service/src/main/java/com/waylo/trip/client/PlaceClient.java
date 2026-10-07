package com.waylo.trip.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * Дані місця з place-service. Потрібні рівно для одного: дотягнути фото до
 * пунктів маршруту, спланованих ще до того, як planner навчився його передавати.
 *
 * Два різні результати, і плутати їх не можна:
 *   порожній Optional — місця немає або фото в нього немає (остаточно);
 *   виняток          — сервіс не відповів, тож питання лишається відкритим.
 * Від цього залежить, чи позначати пункт як перевірений назавжди.
 */
@Component
public class PlaceClient {

    private static final Logger log = LoggerFactory.getLogger(PlaceClient.class);

    private final RestClient restClient;

    public PlaceClient(RestClient.Builder builder, @Value("${app.services.place-uri}") String placeUri) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(placeUri).requestFactory(requestFactory).build();
    }

    public Optional<PlaceLookup> findById(String placeId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/places/{id}", placeId)
                    .retrieve()
                    .body(PlaceLookup.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();      // такого місця в каталозі немає — це відповідь
            }
            throw new PlaceUnavailableException("place-service: " + ex.getStatusCode());
        } catch (Exception ex) {
            log.debug("place-service не відповів про {}: {}", placeId, ex.getMessage());
            throw new PlaceUnavailableException("place-service недоступний: " + ex.getMessage());
        }
    }

    /** Відповіді не отримали — питання лишається відкритим, спробуємо наступного разу. */
    public static class PlaceUnavailableException extends RuntimeException {
        public PlaceUnavailableException(String message) {
            super(message);
        }
    }
}
