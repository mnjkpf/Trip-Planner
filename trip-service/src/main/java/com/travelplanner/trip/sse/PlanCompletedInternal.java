package com.travelplanner.trip.sse;

import java.util.UUID;

/** Внутрішня подія: маршрут поїздки застосовано (для SSE-нотифікації після коміту). */
public record PlanCompletedInternal(UUID tripId) {}
