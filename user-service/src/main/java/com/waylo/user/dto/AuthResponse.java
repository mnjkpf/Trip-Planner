package com.waylo.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,     // завжди "Bearer"
        long expiresIn        // час життя access-токена в секундах
) {}
