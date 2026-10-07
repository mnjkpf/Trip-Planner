package com.waylo.context.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Останній відомий знімок маршруту подорожі (див. міграцію V1). */
@Entity
@Table(name = "watched_trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchedTrip {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "destination_name")
    private String destinationName;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    /** JSON-масив днів; розбирає TripProjection.days(). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String days;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_fingerprint", columnDefinition = "text")
    private String lastFingerprint;
}
