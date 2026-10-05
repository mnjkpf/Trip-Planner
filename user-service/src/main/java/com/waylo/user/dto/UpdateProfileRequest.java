package com.waylo.user.dto;

import jakarta.validation.constraints.Size;

/** Косметичні зміни профілю. Email не змінюємо (це логін). */
public record UpdateProfileRequest(
        @Size(max = 120) String displayName,
        @Size(min = 2, max = 5) String preferredLanguage
) {}
