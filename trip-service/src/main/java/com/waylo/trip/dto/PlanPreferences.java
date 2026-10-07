package com.waylo.trip.dto;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Необовʼязкові побажання до планування. Будь-яке поле може бути null —
 * тоді планувальник бере свій дефолт. Нічого з цього не впливає на валідність
 * подорожі: створити її можна взагалі не відкриваючи цей блок.
 */
public record PlanPreferences(
        /** Темп: скільки місць намагатись вмістити в день. */
        @Pattern(regexp = "RELAXED|BALANCED|PACKED") String pace,

        /** Категорії, які цікавлять найбільше (PARK, MUSEUM, CAFE…). */
        List<@Pattern(regexp = "[A-Z_]{2,20}") String> interests,

        /** Радіус добору POI навколо центру міста, метри. */
        @Min(1000) @Max(50000) Integer searchRadiusM,

        /** О котрій починається день у маршруті. */
        LocalTime dayStartTime
) {}
