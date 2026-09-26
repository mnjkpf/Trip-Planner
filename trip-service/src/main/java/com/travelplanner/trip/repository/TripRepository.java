package com.travelplanner.trip.repository;

import com.travelplanner.trip.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByUserIdOrderByStartDateDesc(UUID userId);

    // Пошук з перевіркою власника одразу — щоб не віддати чужу подорож.
    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);
}
