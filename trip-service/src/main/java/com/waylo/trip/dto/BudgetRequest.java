package com.waylo.trip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Плановий бюджет. Обидва поля опційні: null у amount стирає план
 * (користувач передумав його тримати), і подорож далі просто рахує витрати.
 */
public record BudgetRequest(
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "(?i)[a-z]{3}") String currency
) {}
