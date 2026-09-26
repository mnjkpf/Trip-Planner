package com.travelplanner.planner.planning;

import com.travelplanner.planner.itinerary.ItineraryDay;

import java.util.List;

/** Результат планування — payload події trip.plan.completed (споживає trip-service). */
public record PlanCompletedEvent(
        String jobId,
        String tripId,
        String userId,
        String status,          // COMPLETED
        List<ItineraryDay> days
) {}
