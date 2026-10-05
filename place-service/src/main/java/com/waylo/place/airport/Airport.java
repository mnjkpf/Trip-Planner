package com.waylo.place.airport;

/**
 * Запис про один аеропорт зі списку OpenFlights (топ-500 світу).
 * Лежить у памʼяті (див. {@link AirportCatalog}), ніякої БД.
 */
public record Airport(
    String iata,     // 3-літерний код (напр. "WAW")
    String name,     // "Warsaw Chopin Airport"
    String city,     // "Warsaw"
    String country,  // "Poland"
    double lat,
    double lon
) {}
