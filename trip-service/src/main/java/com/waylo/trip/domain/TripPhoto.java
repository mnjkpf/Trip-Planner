package com.waylo.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Фото подорожі: посилання на обʼєкт у media-service + контекст (див. міграцію V9). */
@Entity
@Table(name = "trip_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPhoto {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "itinerary_item_id")
    private UUID itineraryItemId;

    @Column(name = "place_name")
    private String placeName;

    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PhotoStatus status;

    @Column(name = "content_type", length = 100)
    private String contentType;

    private Long bytes;

    private Integer width;

    private Integer height;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;
}
