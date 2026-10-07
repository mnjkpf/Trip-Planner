package com.waylo.place.ownplace;

/**
 * Результат парсингу посилання на Google Maps: координати + коротка назва.
 * {@code source} — звідки взяли (поки лише "google-maps").
 */
public record OwnPlacePreview(
    String name,
    double lat,
    double lon,
    String source,
    String originalUrl
) {}
