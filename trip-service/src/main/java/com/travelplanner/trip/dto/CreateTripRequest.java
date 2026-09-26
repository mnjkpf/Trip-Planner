package com.travelplanner.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTripRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String destinationName,
        @Size(min = 2, max = 2) String destinationCountry,
        double destinationLat,
        double destinationLon,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
