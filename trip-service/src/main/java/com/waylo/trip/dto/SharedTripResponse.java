package com.waylo.trip.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Те, що бачить гість за публічним посиланням. СВІДОМО вузький DTO: ні id
 * подорожі, ні userId, ні вішліст, ні побажання до планування — лише те, що
 * потрібно подивитись маршрут. Токен не повинен ставати ключем до решти даних.
 */
public record SharedTripResponse(
        String title,
        String destinationName,
        String destinationCountry,
        double destinationLat,
        double destinationLon,
        LocalDate startDate,
        LocalDate endDate,
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
            int order,
            String placeName,
            String placeCategory,
            double lat,
            double lon,
            String time,
            int dwellMinutes,
            Integer travelMinutesFromPrev,
            String note
    ) {}
}
