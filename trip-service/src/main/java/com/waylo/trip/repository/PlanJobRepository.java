package com.waylo.trip.repository;

import com.waylo.trip.domain.PlanJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanJobRepository extends JpaRepository<PlanJob, UUID> {
}
