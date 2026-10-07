package com.waylo.media.event;

import java.time.Instant;

/** Оригінал лежить у сховищі — час робити похідні. */
public record MediaUploadedEvent(
        String mediaId,
        String context,
        String contentType,
        long bytes,
        Instant uploadedAt
) {}
