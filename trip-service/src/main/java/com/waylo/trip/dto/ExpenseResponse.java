package com.waylo.trip.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String category,
        String title,
        BigDecimal amount,
        String currency,
        LocalDate spentOn,
        String note,
        Instant createdAt
) {}
