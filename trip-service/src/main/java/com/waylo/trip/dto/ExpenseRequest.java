package com.waylo.trip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Витрата. currency валідуємо патерном, а не переліком ISO 4217: коди іноді
 * зʼявляються й зникають, а жорсткий список довелось би оновлювати разом із
 * реальністю. Нормалізацію у верхній регістр робить сервіс.
 */
public record ExpenseRequest(
        @NotBlank @Pattern(regexp = "FLIGHT|HOTEL|FOOD|TRANSPORT|ACTIVITY|SHOPPING|OTHER") String category,
        @NotBlank @Size(max = 200) String title,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{3}") String currency,
        LocalDate spentOn,
        @Size(max = 500) String note
) {}
