package com.waylo.trip.repository;

import com.waylo.trip.domain.WeatherAlertDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WeatherAlertDismissalRepository extends JpaRepository<WeatherAlertDismissal, UUID> {

    List<WeatherAlertDismissal> findByUserIdAndAlertIdIn(UUID userId, Collection<UUID> alertIds);

    boolean existsByAlertIdAndUserId(UUID alertId, UUID userId);
}
