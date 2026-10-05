package com.waylo.place.dto;

/** Підказка міста для поля «Напрямок»: назва, країна й координати центру. */
public record CitySuggestion(
        String name,
        String country,
        String countryCode,
        String formatted,
        double lat,
        double lon
) {}
