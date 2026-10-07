package com.waylo.place.provider;

import com.waylo.place.dto.CitySuggestion;

import java.util.List;

/**
 * Абстракція над геокодингом (пошук міста за текстом). Окрема від PlacesProvider:
 * там POI в радіусі, тут — місто й координати його центру.
 */
public interface GeocodeProvider {

    /** Чи налаштований провайдер (є ключ). */
    boolean isEnabled();

    /** Підказки міст за частковим текстом. Порожній список — якщо нічого/недоступно. */
    List<CitySuggestion> suggestCities(String query, int limit);
}
