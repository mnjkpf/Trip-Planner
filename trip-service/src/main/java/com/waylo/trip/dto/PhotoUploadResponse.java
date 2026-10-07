package com.waylo.trip.dto;

import java.util.UUID;

/**
 * Дозвіл на завантаження: рядок фото вже створено (статус UPLOADING), лишилось
 * покласти байти за uploadPath із цим тікетом.
 */
public record PhotoUploadResponse(
        UUID photoId,
        UUID mediaId,
        String ticket,
        String uploadPath,
        long maxBytes,
        String contentTypes
) {}
