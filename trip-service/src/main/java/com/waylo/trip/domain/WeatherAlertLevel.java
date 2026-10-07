package com.waylo.trip.domain;

public enum WeatherAlertLevel {
    RAIN,
    HEAVY_RAIN;

    /** Невідомий рівень від новішого context-service не має валити споживача. */
    public static WeatherAlertLevel parse(String value) {
        return "HEAVY_RAIN".equals(value) ? HEAVY_RAIN : RAIN;
    }
}
