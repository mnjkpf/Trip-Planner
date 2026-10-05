package com.waylo.planner.itinerary;

/**
 * Одна точка дня. order — порядок відвідування (з 1). travelMinutesFromPrev — пішки
 * від попередньої точки; dwellMinutes — скільки там пробути; plannedStart/plannedEnd —
 * розклад "HH:mm". Усе рахує ItineraryPlanner.
 */
public record ItineraryItem(
        String placeId,
        String name,
        String category,
        double lat,
        double lon,
        int order,
        int travelMinutesFromPrev,
        int dwellMinutes,
        String plannedStart,
        String plannedEnd
) {}
