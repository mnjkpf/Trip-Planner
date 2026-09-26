package com.travelplanner.planner.planning;

import com.travelplanner.planner.itinerary.ItineraryDay;

import java.util.List;

/**
 * Результат планування — payload події trip.plan.completed (споживає trip-service).
 * season/climateHint — збагачення від context-service (можуть бути null, якщо сервіс недоступний).
 */
public record PlanCompletedEvent(
        String jobId,
        String tripId,
        String userId,
        String status,          // COMPLETED
        String season,
        String climateHint,
        List<ItineraryDay> days
) {}
