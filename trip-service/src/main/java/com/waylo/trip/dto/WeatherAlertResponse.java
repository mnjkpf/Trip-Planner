package com.waylo.trip.dto;

import com.waylo.trip.domain.WeatherAlertLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Погодне попередження для UI. Тексту тут немає — лише дані: фронт сам складає
 * фразу потрібною мовою (той самий принцип, що й із кодами сезону).
 */
public record WeatherAlertResponse(
        UUID id,
        LocalDate date,
        int dayIndex,
        WeatherAlertLevel level,
        double precipitationMm,
        List<String> outdoorPlaces,
        LocalDate swapDate,
        Integer swapDayIndex,
        Double swapPrecipitationMm,
        Instant generatedAt
) {}
