package com.travelplanner.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        UUID placeId,
        String placeName,
        Double placeLat,
        Double placeLon,
        String note,
        Instant createdAt
) {}
