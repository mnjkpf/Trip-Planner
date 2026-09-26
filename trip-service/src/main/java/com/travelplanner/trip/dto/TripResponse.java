package com.travelplanner.trip.dto;

import com.travelplanner.trip.domain.TripStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TripResponse(
        UUID id,
        UUID userId,
        String title,
        String destinationName,
        String destinationCountry,
        double destinationLat,
        double destinationLon,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        Instant createdAt
) {}
