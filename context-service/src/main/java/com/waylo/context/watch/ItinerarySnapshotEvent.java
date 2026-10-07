package com.waylo.context.watch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Подія trip.itinerary.snapshot від trip-service — повний стан маршруту.
 * Обгорткові типи навмисно: у знімку видалення (deleted=true) є лише tripId і час.
 */
public record ItinerarySnapshotEvent(
        String tripId,
        Boolean deleted,
        String destinationName,
        Double lat,
        Double lon,
        LocalDate startDate,
        LocalDate endDate,
        Instant snapshotAt,
        List<Day> days
) {
    public record Day(Integer dayIndex, LocalDate date, List<Place> places) {}

    public record Place(String name, String category) {}
}
