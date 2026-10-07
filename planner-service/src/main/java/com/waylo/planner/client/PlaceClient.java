package com.waylo.planner.client;

import java.util.List;

/**
 * Абстракція над place-service. Інтерфейс (а не прямий RestClient) — щоб у тестах
 * легко підмінити заглушкою, а бізнес-логіку планувальника перевіряти без мережі.
 */
public interface PlaceClient {

    /** POI навколо точки призначення в межах радіуса (метри). */
    List<PlaceDto> searchNearby(double lat, double lon, int radiusMeters);
}
