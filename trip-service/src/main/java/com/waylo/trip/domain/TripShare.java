package com.waylo.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Публічне посилання на маршрут. Відкликання — м'яке (revokedAt), щоб у БД
 * лишався слід: коли посилання створили й коли закрили.
 */
@Entity
@Table(name = "trip_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripShare {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
