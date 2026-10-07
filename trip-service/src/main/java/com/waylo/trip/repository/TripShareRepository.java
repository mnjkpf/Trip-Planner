package com.waylo.trip.repository;

import com.waylo.trip.domain.TripShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripShareRepository extends JpaRepository<TripShare, UUID> {

    /** Активне посилання подорожі (на одну подорож їх не більше одного). */
    Optional<TripShare> findByTripIdAndRevokedAtIsNull(UUID tripId);

    /** Публічний вхід за токеном; відкликані не віддаємо. */
    Optional<TripShare> findByTokenAndRevokedAtIsNull(String token);
}
