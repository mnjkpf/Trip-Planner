package com.waylo.trip.consumer;

import com.waylo.trip.service.WeatherAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Споживач trip.weather.alert: зберігає пораду, а UI дізнається про неї через SSE. */
@Component
public class WeatherAlertListener {

    private static final Logger log = LoggerFactory.getLogger(WeatherAlertListener.class);

    private final JsonMapper jsonMapper;
    private final WeatherAlertService weatherAlertService;

    public WeatherAlertListener(JsonMapper jsonMapper, WeatherAlertService weatherAlertService) {
        this.jsonMapper = jsonMapper;
        this.weatherAlertService = weatherAlertService;
    }

    @KafkaListener(topics = "${app.topics.weather-alert}")
    public void onWeatherAlert(String payload) {
        WeatherAlertEvent event = jsonMapper.readValue(payload, WeatherAlertEvent.class);
        log.info("отримано trip.weather.alert: trip={}, попереджень={}",
                event.tripId(), event.alerts() == null ? 0 : event.alerts().size());
        weatherAlertService.apply(event);
    }
}
