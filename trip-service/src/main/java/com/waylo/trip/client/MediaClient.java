package com.waylo.trip.client;

import com.waylo.trip.error.ApiExceptions.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * Запит тікета на завантаження у media-service.
 *
 * Це єдиний синхронний виклик у бік медіа — і він свідомо такий: права знає
 * лише trip-service, тож тікет має видаватись у відповідь на дію користувача,
 * тут і зараз. Усе інше (готовність файлу, видалення) їде подіями.
 */
@Component
public class MediaClient {

    private final RestClient restClient;

    public MediaClient(RestClient.Builder builder, @Value("${app.services.media-uri}") String mediaUri) {
        this.restClient = builder.baseUrl(mediaUri).build();
    }

    public MediaTicket requestTicket(UUID tripId, UUID userId) {
        try {
            MediaTicket ticket = restClient.post()
                    .uri("/internal/media/tickets")
                    .body(Map.of("context", "trip:" + tripId, "userId", userId.toString()))
                    .retrieve()
                    .body(MediaTicket.class);
            if (ticket == null) {
                throw new ServiceUnavailableException("Сервіс фото не відповів — спробуй пізніше");
            }
            return ticket;
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Сервіс фото недоступний — спробуй пізніше");
        }
    }
}
