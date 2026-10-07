package com.waylo.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        String placeId,
        String placeName,
        Double placeLat,
        Double placeLon,
        String note,
        Instant createdAt
) {}
