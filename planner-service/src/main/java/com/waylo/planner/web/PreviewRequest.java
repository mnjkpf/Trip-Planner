package com.waylo.planner.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Те саме, що PlanRequest, але без jobId/tripId/userId: прев'ю будується до
 * того, як подорож узагалі з'явиться, і для людини без акаунта.
 */
public record PreviewRequest(
        @NotNull Double destinationLat,
        @NotNull Double destinationLon,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String pace,
        List<String> interests,
        @Min(1000) @Max(50000) Integer searchRadiusM,
        LocalTime dayStartTime
) {}
