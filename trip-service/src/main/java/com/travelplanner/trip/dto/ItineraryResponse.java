package com.travelplanner.trip.dto;

import com.travelplanner.trip.domain.TripStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Готовий маршрут для читання фронтом: дні по порядку, у кожному — пункти по порядку.
 * Сезон/погоду фронт бере окремо з context-service (тут вони не зберігаються).
 */
public record ItineraryResponse(
        UUID tripId,
        TripStatus status,
        List<Day> days
) {
    public record Day(int dayIndex, LocalDate date, List<Item> items) {}

    public record Item(
            int order,
            UUID placeId,
            String placeName,
            String placeCategory,
            double lat,
            double lon
    ) {}
}
