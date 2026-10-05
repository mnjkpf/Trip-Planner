package com.waylo.place.domain;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "places")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Place {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Зберігаємо назву категорії рядком. Тримати тут PlaceCategory як enum теж
    // можна, але VARCHAR лишає простір для категорій провайдерів, яких ще нема
    // в нашому переліку. Нормалізацію робить сервіс/маппер.
    @Column(nullable = false)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    // JTS Point у SRID 4326. Колонка — geometry(Point,4326).
    @Column(name = "location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    private String city;
    private String address;

    @Column(name = "opening_hours")
    private String openingHours;

    private String website;
    private String phone;

    @Column(name = "wikidata_id")
    private String wikidataId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "source_provider", nullable = false)
    private String sourceProvider;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
