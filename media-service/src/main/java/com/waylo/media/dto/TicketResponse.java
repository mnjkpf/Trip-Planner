package com.waylo.media.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Тікет для клієнта. uploadPath віддаємо звідси, щоб фронт не зашивав у себе
 * шлях медіа-сервісу: сервер скаже, куди слати файл.
 */
public record TicketResponse(
        String ticket,
        UUID mediaId,
        String uploadPath,
        long maxBytes,
        String contentTypes,
        Instant expiresAt
) {}
