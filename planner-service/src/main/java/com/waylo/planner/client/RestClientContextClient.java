package com.waylo.planner.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;

/**
 * Виклик context-service (/api/context) через circuit breaker. Деградує мʼяко:
 * коло відкрите або сервіс недоступний — порожній контекст, маршрут без сезону.
 * executeSupplier на потоці виклику -> trace-контекст зберігається.
 */
@Component
public class RestClientContextClient implements ContextClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientContextClient.class);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final CircuitBreaker circuitBreaker;

    public RestClientContextClient(RestClient.Builder builder,
                                   JsonMapper jsonMapper,
                                   CircuitBreakerRegistry registry,
                                   @Value("${app.services.context-uri}") String contextUri) {
        this.restClient = builder.baseUrl(contextUri).build();
        this.jsonMapper = jsonMapper;
        this.circuitBreaker = registry.circuitBreaker("context-service");
    }

    @Override
    public DestinationContext fetch(double lat, double lon, LocalDate start, LocalDate end) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                String body = restClient.get()
                        .uri(b -> b.path("/api/context")
                                .queryParam("lat", lat)
                                .queryParam("lon", lon)
                                .queryParam("startDate", start)
                                .queryParam("endDate", end)
                                .build())
                        .retrieve()
                        .body(String.class);
                Map<String, Object> m = parse(body);
                return new DestinationContext((String) m.get("season"), (String) m.get("climateHint"));
            });
        } catch (Exception ex) {
            log.warn("context-service недоступний ({}) — маршрут без контексту", ex.getMessage());
            return DestinationContext.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        return (body == null || body.isBlank()) ? Map.of() : jsonMapper.readValue(body, Map.class);
    }
}
