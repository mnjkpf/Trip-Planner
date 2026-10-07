package com.waylo.trip.sse;

import java.util.UUID;

/** Внутрішня подія: погодні попередження подорожі змінились (SSE після коміту). */
public record WeatherAlertsChanged(UUID tripId) {}
