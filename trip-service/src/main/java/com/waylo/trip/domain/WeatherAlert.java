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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Погодне попередження на один день подорожі (див. міграцію V8). */
@Entity
@Table(name = "trip_weather_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherAlert {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WeatherAlertLevel level;

    @Column(name = "precipitation_mm", nullable = false)
    private double precipitationMm;

    /** Назви через '\n'. */
    @Column(name = "outdoor_places", nullable = false, columnDefinition = "text")
    private String outdoorPlaces;

    @Column(name = "swap_date")
    private LocalDate swapDate;

    @Column(name = "swap_day_index")
    private Integer swapDayIndex;

    @Column(name = "swap_precipitation_mm")
    private Double swapPrecipitationMm;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
