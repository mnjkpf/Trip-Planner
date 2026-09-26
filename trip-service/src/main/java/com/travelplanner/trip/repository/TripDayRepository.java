package com.travelplanner.trip.repository;

import com.travelplanner.trip.domain.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripDayRepository extends JpaRepository<TripDay, UUID> {

    List<TripDay> findByTripIdOrderByDayIndexAsc(UUID tripId);
}
