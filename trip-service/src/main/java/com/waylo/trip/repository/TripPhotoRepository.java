package com.waylo.trip.repository;

import com.waylo.trip.domain.PhotoStatus;
import com.waylo.trip.domain.TripPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripPhotoRepository extends JpaRepository<TripPhoto, UUID> {

    List<TripPhoto> findByTripIdOrderByCreatedAtAsc(UUID tripId);

    List<TripPhoto> findByTripIdAndStatusOrderByCreatedAtAsc(UUID tripId, PhotoStatus status);

    Optional<TripPhoto> findByMediaId(UUID mediaId);

    Optional<TripPhoto> findByIdAndTripId(UUID id, UUID tripId);

    /** Недовантажені «висяки» — для планового прибирання. */
    List<TripPhoto> findByStatusAndCreatedAtBefore(PhotoStatus status, Instant before);

    long countByTripId(UUID tripId);
}
