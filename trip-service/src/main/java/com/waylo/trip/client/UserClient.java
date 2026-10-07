package com.waylo.trip.client;

import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Пошук користувача за поштою в user-service — єдина причина, через яку
 * trip-service взагалі знає про його існування.
 *
 * Circuit breaker тут навмисно НЕ ставимо, на відміну від викликів до
 * place-service у планувальнику: там мʼяка деградація має сенс (маршрут без
 * місць), а запрошення без підтвердження пошти — ні. Краще чесно впасти.
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(RestClient.Builder builder, @Value("${app.services.user-uri}") String userUri) {
        this.restClient = builder.baseUrl(userUri).build();
    }

    /** Порожньо, якщо такої пошти немає (user-service віддає 404). */
    public Optional<UserLookup> findByEmail(String email) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(b -> b.path("/api/user/lookup").queryParam("email", email).build())
                    .retrieve()
                    .body(UserLookup.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new TripNotFoundException("Не вдалося перевірити пошту — спробуй пізніше");
        }
    }
}
