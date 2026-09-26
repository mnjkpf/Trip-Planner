package com.travelplanner.planner.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;

/**
 * Реальний виклик context-service (/api/context). Деградує мʼяко: якщо сервіс
 * недоступний — повертаємо порожній контекст, і маршрут будується без сезону.
 */
@Component
public class RestClientContextClient implements ContextClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientContextClient.class);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public RestClientContextClient(RestClient.Builder builder,
                                   JsonMapper jsonMapper,
                                   @Value("${app.services.context-uri}") String contextUri) {
        this.restClient = builder.baseUrl(contextUri).build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public DestinationContext fetch(double lat, double lon, LocalDate start, LocalDate end) {
        try {
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
