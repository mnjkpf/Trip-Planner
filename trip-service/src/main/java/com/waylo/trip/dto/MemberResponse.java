package com.waylo.trip.dto;

import com.waylo.trip.domain.TripRole;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        UUID userId,
        String email,
        TripRole role,
        /** true для того, хто саме дивиться список — щоб UI підсвітив «це ти». */
        boolean self,
        Instant createdAt
) {}
