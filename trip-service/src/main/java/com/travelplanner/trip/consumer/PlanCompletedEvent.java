package com.travelplanner.trip.consumer;

import java.time.LocalDate;
import java.util.List;

/**
 * Подія trip.plan.completed від planner-service. Дзеркалить payload планувальника
 * (Jackson мапить за іменами компонентів record — увімкнено -parameters).
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
            double lat,
            double lon,
            int order
    ) {}
}
