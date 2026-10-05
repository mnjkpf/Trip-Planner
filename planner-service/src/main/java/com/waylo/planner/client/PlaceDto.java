package com.waylo.planner.client;

/**
 * Підмножина відповіді place-service (/api/places/search), потрібна планувальнику.
 * category — для оцінки тривалості перебування (dwell). Зайві поля Jackson ігнорує.
 */
public record PlaceDto(
        String id,
        String name,
        String category,
        double lat,
        double lon
) {}
