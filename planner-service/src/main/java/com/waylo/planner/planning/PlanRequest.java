package com.waylo.planner.planning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Розпарсена подія trip.plan.requested — вхідні дані для побудови маршруту.
 *
 * Поля побажань (pace, interests, searchRadiusM, dayStartTime) — ОПЦІЙНІ:
 * користувач може нічого не вибирати, тоді вони приходять null і планувальник
 * працює на своїх дефолтах. Саме тому це boxed Integer, а не int.
 */
public record PlanRequest(
        String jobId,
        String tripId,
        String userId,
        double destinationLat,
        double destinationLon,
        LocalDate startDate,
        LocalDate endDate,
        String pace,
        List<String> interests,
        Integer searchRadiusM,
        LocalTime dayStartTime
) {

    /** Без побажань — зручно в тестах і для подій зі старою схемою. */
    public PlanRequest(String jobId, String tripId, String userId,
                       double destinationLat, double destinationLon,
                       LocalDate startDate, LocalDate endDate) {
        this(jobId, tripId, userId, destinationLat, destinationLon,
                startDate, endDate, null, List.of(), null, null);
    }

    public PlanRequest {
        interests = interests == null ? List.of() : List.copyOf(interests);
    }
}
