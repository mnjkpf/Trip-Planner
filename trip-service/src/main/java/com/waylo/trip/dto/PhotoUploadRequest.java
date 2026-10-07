package com.waylo.trip.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Запит на завантаження фото. Усе опційне: фото може бути «просто з подорожі»,
 * без прив'язки до конкретного місця.
 */
public record PhotoUploadRequest(
        UUID itemId,
        @Size(max = 300) String caption
) {}
