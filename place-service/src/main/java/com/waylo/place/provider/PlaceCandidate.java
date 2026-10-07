package com.waylo.place.provider;

import com.waylo.place.domain.PlaceCategory;

/**
 * Нормалізований результат від провайдера — ще НЕ сутність Place.
 * Домен не залежить від формату відповіді конкретного API: маппер провайдера
 * повертає саме PlaceCandidate, а сервіс уже вирішує, зберігати чи ні.
 */
public record PlaceCandidate(
        String provider,
        String externalId,
        String name,
        PlaceCategory category,
        double lat,
        double lon,
        String city,
        String countryCode,
        String address,
        String website,
        String imageUrl,
        String rawPayload
) {}
