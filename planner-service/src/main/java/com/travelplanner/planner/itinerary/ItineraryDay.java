package com.travelplanner.planner.itinerary;

import java.time.LocalDate;
import java.util.List;

/** День маршруту: номер (з 1), дата й впорядкований перелік точок. */
public record ItineraryDay(
        int dayNumber,
        LocalDate date,
        List<ItineraryItem> items
) {}
