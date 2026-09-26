package com.travelplanner.place.provider;

import com.travelplanner.place.domain.PlaceCategory;

import java.util.List;

/**
 * Абстракція над зовнішнім джерелом POI (Geoapify, Overpass, OpenTripMap…).
 * Сервіс працює тільки з цим інтерфейсом — конкретний провайдер можна замінити
 * чи додати fallback, не чіпаючи бізнес-логіку.
 */
public interface PlacesProvider {

    /** Коротке імʼя провайдера (йде в Place.sourceProvider і в rate limiter). */
    String name();

    /** Пошук POI навколо точки. Реалізація мапить відповідь у PlaceCandidate. */
    List<PlaceCandidate> searchNearby(double lat, double lon, int radiusMeters, PlaceCategory category);
}
