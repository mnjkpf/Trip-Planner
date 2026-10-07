package com.waylo.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// placeId може бути UUID від place-service АБО довільний рядок типу "custom:abc"
// для місць, доданих користувачем через посилання Google Maps.
public record WishlistItemRequest(
        @NotBlank @Size(max = 100) String placeId,
        @NotBlank String placeName,
        Double placeLat,
        Double placeLon,
        String note
) {}
