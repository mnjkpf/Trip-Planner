package com.waylo.trip.dto;

import com.waylo.trip.domain.TripRole;
import com.waylo.trip.domain.TripStatus;

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
        String originAirport,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        PlanPreferences preferences,
        /** Роль того, хто запитує — фронт по ній ховає кнопки редагування. */
        TripRole role,
        Instant createdAt
) {}
