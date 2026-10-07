package com.waylo.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Налаштування медіа з application.yml (app.media.*). */
@Component
public class MediaProperties {

    private final String bucket;
    private final long maxBytes;
    private final Duration ticketTtl;
    private final int thumbMaxPx;
    private final float thumbQuality;
    private final Set<String> contentTypes;

    public MediaProperties(@Value("${app.media.bucket}") String bucket,
                           @Value("${app.media.max-bytes}") long maxBytes,
                           @Value("${app.media.ticket-ttl}") Duration ticketTtl,
                           @Value("${app.media.thumb-max-px}") int thumbMaxPx,
                           @Value("${app.media.thumb-quality}") float thumbQuality,
                           @Value("${app.media.content-types}") String contentTypes) {
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.ticketTtl = ticketTtl;
        this.thumbMaxPx = thumbMaxPx;
        this.thumbQuality = thumbQuality;
        this.contentTypes = new LinkedHashSet<>(Arrays.asList(contentTypes.toLowerCase().split(",")));
    }

    public String bucket() {
        return bucket;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public Duration ticketTtl() {
        return ticketTtl;
    }

    public int thumbMaxPx() {
        return thumbMaxPx;
    }

    public float thumbQuality() {
        return thumbQuality;
    }

    public Set<String> contentTypes() {
        return contentTypes;
    }

    /** Тип приймаємо без параметрів (`image/jpeg; charset=...` теж валідний). */
    public boolean allows(String contentType) {
        if (contentType == null) {
            return false;
        }
        String bare = contentType.split(";")[0].trim().toLowerCase();
        return contentTypes.contains(bare);
    }
}
