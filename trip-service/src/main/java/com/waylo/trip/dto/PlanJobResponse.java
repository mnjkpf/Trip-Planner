package com.waylo.trip.dto;

import com.waylo.trip.domain.PlanJobStatus;

import java.time.Instant;
import java.util.UUID;

public record PlanJobResponse(
        UUID jobId,
        UUID tripId,
        PlanJobStatus status,
        Instant requestedAt
) {}
