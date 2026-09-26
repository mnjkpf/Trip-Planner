package com.travelplanner.planner.itinerary;

/** Одна точка в межах дня маршруту. order — порядок відвідування в цей день (з 1). */
public record ItineraryItem(
        String placeId,
        String name,
        double lat,
        double lon,
        int order
) {}
