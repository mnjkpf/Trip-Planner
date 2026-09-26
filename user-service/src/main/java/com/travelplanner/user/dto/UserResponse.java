package com.travelplanner.user.dto;

import com.travelplanner.user.domain.RoleName;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        RoleName role,
        Instant createdAt
) {}
