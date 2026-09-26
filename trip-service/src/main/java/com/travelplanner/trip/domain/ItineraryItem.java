package com.travelplanner.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Пункт маршруту в межах дня. Наповнюється консюмером trip.plan.completed
 * (наступний крок) — тому контролера для нього поки нема, лише сутність і схема.
 */
@Entity
@Table(name = "itinerary_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryItem {

    @Id
    private UUID id;

    @Column(name = "trip_day_id", nullable = false)
    private UUID tripDayId;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    // Денормалізований снапшот місця
    @Column(name = "place_name", nullable = false)
    private String placeName;

    @Column(name = "place_category")
    private String placeCategory;

    @Column(name = "place_lat", nullable = false)
    private double placeLat;

    @Column(name = "place_lon", nullable = false)
    private double placeLon;

    @Column(name = "place_image_url")
    private String placeImageUrl;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "planned_start")
    private LocalTime plannedStart;

    @Column(name = "planned_end")
    private LocalTime plannedEnd;

    @Column(name = "dwell_minutes", nullable = false)
    private int dwellMinutes;

    @Column(name = "travel_mode_from_prev")
    private String travelModeFromPrev;

    @Column(name = "travel_minutes_from_prev")
    private Integer travelMinutesFromPrev;

    @Column(nullable = false)
    private boolean locked;

    @Column(columnDefinition = "text")
    private String note;
}
