package com.travelplanner.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WishlistItemRequest(
        @NotNull UUID placeId,
        @NotBlank String placeName,
        Double placeLat,
        Double placeLon,
        String note
) {}
