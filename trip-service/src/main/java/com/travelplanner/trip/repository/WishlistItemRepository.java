package com.travelplanner.trip.repository;

import com.travelplanner.trip.domain.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<WishlistItem> findByUserIdAndPlaceId(UUID userId, UUID placeId);
}
