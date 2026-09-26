package com.travelplanner.trip.dto;

import com.travelplanner.trip.domain.PlanJobStatus;

import java.time.Instant;
import java.util.UUID;

public record PlanJobResponse(
        UUID jobId,
        UUID tripId,
        PlanJobStatus status,
        Instant requestedAt
) {}
