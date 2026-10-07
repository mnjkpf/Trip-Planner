package com.waylo.trip.repository;

import com.waylo.trip.domain.WeatherAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeatherAlertRepository extends JpaRepository<WeatherAlert, UUID> {

    List<WeatherAlert> findByTripId(UUID tripId);

    /** Актуальні попередження: минулі дні вже нікому не цікаві. */
    List<WeatherAlert> findByTripIdAndDayDateGreaterThanEqualOrderByDayDateAsc(UUID tripId, LocalDate from);

    Optional<WeatherAlert> findByIdAndTripId(UUID id, UUID tripId);
}
