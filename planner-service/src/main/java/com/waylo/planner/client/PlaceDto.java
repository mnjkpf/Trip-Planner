package com.waylo.planner.client;

/**
 * Підмножина відповіді place-service (/api/places/search), потрібна планувальнику.
 * category — для оцінки тривалості перебування (dwell); imageUrl — фото місця
 * (place-service бере його з Wikipedia), яке ми просто передаємо далі в маршрут.
 * Зайві поля Jackson ігнорує.
 */
public record PlaceDto(
        String id,
        String name,
        String category,
        double lat,
        double lon,
        String imageUrl
) {}
