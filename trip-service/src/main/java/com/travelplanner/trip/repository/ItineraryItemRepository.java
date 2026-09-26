package com.travelplanner.trip.repository;

import com.travelplanner.trip.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    List<ItineraryItem> findByTripDayIdOrderByOrderIndexAsc(UUID tripDayId);

    // Для оновлення снапшотів консюмером place.enriched (наступний крок).
    List<ItineraryItem> findByPlaceId(UUID placeId);
}
