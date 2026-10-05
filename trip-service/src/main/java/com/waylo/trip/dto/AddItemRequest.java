package com.waylo.trip.dto;

import jakarta.validation.constraints.NotBlank;

/** Додати місце (зазвичай із вішлісту) у день маршруту. */
public record AddItemRequest(
        @NotBlank String placeId,
        @NotBlank String placeName,
        String category,
        double lat,
        double lon
) {}
