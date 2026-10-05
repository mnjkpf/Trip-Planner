package com.waylo.context.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Погода з Open-Meteo (безкоштовно, без ключа). Деградує мʼяко: якщо дати поза
 * горизонтом прогнозу або сервіс недоступний — повертаємо порожньо, і контекст
 * лишається з самим сезоном.
 */
@Component
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);

    private final RestClient restClient;
    private final OpenMeteoMapper mapper;

    public OpenMeteoWeatherClient(RestClient.Builder builder,
                                  OpenMeteoMapper mapper,
                                  @Value("${app.providers.open-meteo.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    @Override
    public List<DailyWeather> forecast(double lat, double lon, LocalDate start, LocalDate end) {
        try {
            String body = restClient.get()
                    .uri(b -> b.path("/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("daily", "temperature_2m_min,temperature_2m_max,precipitation_sum")
                            .queryParam("start_date", start)
                            .queryParam("end_date", end)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(String.class);
            List<DailyWeather> days = mapper.parse(body);
            log.info("open-meteo: {} днів прогнозу (lat={}, lon={})", days.size(), lat, lon);
            return days;
        } catch (Exception ex) {
            log.warn("open-meteo недоступний ({}) — контекст без погоди", ex.getMessage());
            return List.of();
        }
    }
}
