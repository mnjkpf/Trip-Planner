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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "destination_name", nullable = false)
    private String destinationName;

    @Column(name = "destination_country", length = 2)
    private String destinationCountry;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lon", nullable = false)
    private double destinationLon;

    /** IATA аеропорту відльоту (напр. "WAW"); nullable для старих подорожей. */
    @Column(name = "origin_airport", length = 3)
    private String originAirport;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // ── опційні налаштування планування (null → дефолти планувальника) ──

    /** RELAXED | BALANCED | PACKED — скільки місць на день. */
    @Column(length = 16)
    private String pace;

    /** CSV категорій, які цікавлять: "PARK,BEACH,MUSEUM". Порожнє = всі однаково. */
    @Column(length = 200)
    private String interests;

    @Column(name = "search_radius_m")
    private Integer searchRadiusM;

    @Column(name = "day_start_time")
    private LocalTime dayStartTime;

    /** Плановий бюджет. BigDecimal, бо це гроші — див. коментар у міграції V6. */
    @Column(name = "budget_amount", precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
