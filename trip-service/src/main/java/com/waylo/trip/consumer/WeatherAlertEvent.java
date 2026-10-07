package com.waylo.trip.consumer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Подія trip.weather.alert від context-service: ПОВНИЙ поточний набір порад для
 * подорожі. Порожній список означає «попереджати більше нема про що».
 */
public record WeatherAlertEvent(
        String tripId,
        Instant generatedAt,
        List<Alert> alerts
) {
    public record Alert(
            LocalDate date,
            Integer dayIndex,
            String level,
            Double precipitationMm,
            List<String> outdoorPlaces,
            LocalDate swapDate,
            Integer swapDayIndex,
            Double swapPrecipitationMm
    ) {}
}
