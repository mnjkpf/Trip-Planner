package com.waylo.trip.client;

import java.time.Instant;
import java.util.UUID;

/** Відповідь media-service на запит тікета (дзеркалить його TicketResponse). */
public record MediaTicket(
        String ticket,
        UUID mediaId,
        String uploadPath,
        long maxBytes,
        String contentTypes,
        Instant expiresAt
) {}
