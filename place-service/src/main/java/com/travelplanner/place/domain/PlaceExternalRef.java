package com.travelplanner.place.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Звʼязок канонічного місця з його id у зовнішнього провайдера.
 * Унікальність по (provider, external_id) — щоб той самий обʼєкт з того самого
 * джерела не задублювався.
 */
@Entity
@Table(name = "place_external_refs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceExternalRef {

    @Id
    private UUID id;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    // Сира відповідь провайдера (JSONB). Тримаємо як рядок; @JdbcTypeCode(JSON)
    // каже Hibernate мапити її на jsonb, щоб ddl-auto: validate проходив.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "fetched_at", updatable = false)
    private Instant fetchedAt;
}
