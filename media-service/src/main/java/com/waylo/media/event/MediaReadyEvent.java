package com.waylo.media.event;

import java.time.Instant;

/**
 * Файл готовий до показу: оригінал на місці, мініатюра теж (або її свідомо
 * немає — thumb=false, тоді віддається оригінал). width/height можуть бути
 * null, якщо формат не читається ImageIO.
 */
public record MediaReadyEvent(
        String mediaId,
        String context,
        String contentType,
        long bytes,
        Integer width,
        Integer height,
        boolean thumb,
        Instant readyAt
) {}
