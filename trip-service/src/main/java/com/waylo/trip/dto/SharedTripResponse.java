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
        List<Day> days,
        List<Photo> photos
) {
    public record Day(
            int dayIndex,
            LocalDate date,
            double distanceKm,
            int walkMinutes,
            List<Item> items
    ) {}

    /** Фото без автора й без id рядка: гостю досить картинки та підпису. */
    public record Photo(
            String url,
            String thumbUrl,
            String caption,
            String placeName,
            Integer width,
            Integer height
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
            String note,
            String imageUrl
    ) {}
}
