package com.waylo.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import jakarta.validation.Valid;

import java.time.LocalDate;

// Ті самі поля, що й при створенні: редактор дозволяє змінити все, крім
// власника й статусу. Перевірку порядку дат робимо в сервісі (крос-поле).
public record UpdateTripRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String destinationName,
        @Size(min = 2, max = 2) String destinationCountry,
        double destinationLat,
        double destinationLon,
        @Size(min = 3, max = 3) String originAirport,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Valid PlanPreferences preferences
) {}
