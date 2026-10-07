package com.waylo.trip.dto;

import com.waylo.trip.domain.PhotoStatus;
import com.waylo.trip.domain.TripPhoto;

import java.time.Instant;
import java.util.UUID;

/**
 * Фото для UI. Шляхи до байтів будує бекенд — фронт не має знати, як саме
 * влаштований media-service.
 */
public record PhotoResponse(
        UUID id,
        UUID mediaId,
        String url,
        String thumbUrl,
        String caption,
        String placeName,
        UUID itemId,
        Integer width,
        Integer height,
        PhotoStatus status,
        boolean mine,
        Instant createdAt
) {
    public static PhotoResponse of(TripPhoto p, UUID viewerId) {
        return new PhotoResponse(
                p.getId(),
                p.getMediaId(),
                "/api/media/" + p.getMediaId(),
                "/api/media/" + p.getMediaId() + "/thumb",
                p.getCaption(),
                p.getPlaceName(),
                p.getItineraryItemId(),
                p.getWidth(),
                p.getHeight(),
                p.getStatus(),
                p.getUploadedBy().equals(viewerId),
                p.getCreatedAt());
    }
}
