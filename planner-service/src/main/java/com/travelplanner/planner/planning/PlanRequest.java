package com.travelplanner.planner.planning;

import java.time.LocalDate;

/** Розпарсена подія trip.plan.requested — вхідні дані для побудови маршруту. */
public record PlanRequest(
        String jobId,
        String tripId,
        String userId,
        double destinationLat,
        double destinationLon,
        LocalDate startDate,
        LocalDate endDate
) {}
