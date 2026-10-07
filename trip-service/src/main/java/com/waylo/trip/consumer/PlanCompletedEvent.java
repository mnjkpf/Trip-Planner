package com.waylo.trip.consumer;

import java.time.LocalDate;
import java.util.List;

/**
 * Подія trip.plan.completed від planner-service. Дзеркалить payload планувальника
 * (Jackson мапить за іменами компонентів record — увімкнено -parameters).
 * Нові поля (category/час/dwell/travel) можуть бути відсутні у старих подіях —
 * тому обʼєкти/Integer, а не примітиви.
 */
public record PlanCompletedEvent(
        String jobId,
        String tripId,
        String userId,
        String status,
        List<Day> days
) {
    public record Day(
            int dayNumber,
            LocalDate date,
            List<Item> items
    ) {}

    public record Item(
            String placeId,
            String name,
            String category,
            double lat,
            double lon,
            int order,
            Integer travelMinutesFromPrev,
            Integer dwellMinutes,
            String plannedStart,
            String plannedEnd,
            String imageUrl
    ) {}
}
