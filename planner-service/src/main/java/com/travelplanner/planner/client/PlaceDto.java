package com.travelplanner.planner.client;

/**
 * Підмножина відповіді place-service (/api/places/search), яка потрібна планувальнику.
 * Зайві поля Jackson ігнорує (fail-on-unknown вимкнено за замовчуванням у Spring Boot).
 */
public record PlaceDto(
        String id,
        String name,
        double lat,
        double lon
) {}
