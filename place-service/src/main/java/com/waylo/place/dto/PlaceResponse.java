package com.waylo.place.dto;

import java.util.UUID;

public record PlaceResponse(
        UUID id,
        String name,
        String category,
        String description,
        double lat,
        double lon,
        String city,
        String countryCode,
        String address,
        String imageUrl,
        String website
) {}
