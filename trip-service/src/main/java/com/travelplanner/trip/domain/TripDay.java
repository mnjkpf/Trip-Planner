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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trip_days")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripDay {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;
}
