package com.waylo.trip.dto;

import com.waylo.trip.domain.TripStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Готовий маршрут для фронта: дні по порядку, у кожному — пункти по порядку,
 * час візиту, дистанція пішки за день, хвилини ходьби, плюс поля для редагування
 * (id пункту, locked, note).
 */
public record ItineraryResponse(
        UUID tripId,
        TripStatus status,
        List<Day> days
) {
    public record Day(
            int dayIndex,
            LocalDate date,
            double distanceKm,
            int walkMinutes,
            List<Item> items
    ) {}

    public record Item(
            UUID id,
            int order,
            String placeId,
            String placeName,
            String placeCategory,
            double lat,
            double lon,
            String time,
            int dwellMinutes,
            Integer travelMinutesFromPrev,
            boolean locked,
            String note
    ) {}
}
