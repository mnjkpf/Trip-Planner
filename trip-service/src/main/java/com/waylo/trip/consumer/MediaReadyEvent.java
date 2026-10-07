package com.waylo.trip.consumer;

import java.time.Instant;

/**
 * Подія media.ready від media-service: байти на місці, мініатюра зроблена
 * (або свідомо відсутня — thumb=false, тоді показується оригінал).
 */
public record MediaReadyEvent(
        String mediaId,
        String context,
        String contentType,
        Long bytes,
        Integer width,
        Integer height,
        Boolean thumb,
        Instant readyAt
) {}
