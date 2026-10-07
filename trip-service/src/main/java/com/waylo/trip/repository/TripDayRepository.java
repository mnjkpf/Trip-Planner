package com.waylo.trip.repository;

import com.waylo.trip.domain.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripDayRepository extends JpaRepository<TripDay, UUID> {

    List<TripDay> findByTripIdOrderByDayIndexAsc(UUID tripId);

    Optional<TripDay> findByTripIdAndDayIndex(UUID tripId, int dayIndex);
}
