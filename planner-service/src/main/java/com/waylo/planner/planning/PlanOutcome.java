package com.waylo.planner.planning;

import com.waylo.planner.itinerary.ItineraryDay;

import java.util.List;

/**
 * Результат побудови маршруту без прив'язки до подорожі, job'а чи користувача.
 * Саме це спільне для асинхронного планування й публічного прев'ю: перше
 * загортає його в подію Kafka, друге віддає просто в HTTP-відповідь.
 */
public record PlanOutcome(
        List<ItineraryDay> days,
        String season,
        String climateHint
) {}
