package com.waylo.trip.sse;

import java.util.UUID;

/** Внутрішня подія: склад фото подорожі змінився (SSE після коміту). */
public record PhotosChanged(UUID tripId) {}
