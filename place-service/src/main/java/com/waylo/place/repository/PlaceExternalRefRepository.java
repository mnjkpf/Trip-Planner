package com.waylo.place.repository;

import com.waylo.place.domain.PlaceExternalRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlaceExternalRefRepository extends JpaRepository<PlaceExternalRef, UUID> {

    Optional<PlaceExternalRef> findByProviderAndExternalId(String provider, String externalId);

    boolean existsByProviderAndExternalId(String provider, String externalId);
}
