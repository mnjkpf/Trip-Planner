package com.waylo.user.dto;

import com.waylo.user.domain.RoleName;

import java.time.Instant;
import java.util.UUID;

/** Профіль поточного користувача (GET /api/user/me). */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String preferredLanguage,
        /** null = локальна реєстрація; "google" = вхід через Google (пароля немає). */
        String oauthProvider,
        RoleName role,
        Instant createdAt
) {}
