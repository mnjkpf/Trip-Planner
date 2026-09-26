package com.travelplanner.trip.service;

import com.travelplanner.trip.domain.WishlistItem;
import com.travelplanner.trip.dto.WishlistItemRequest;
import com.travelplanner.trip.dto.WishlistItemResponse;
import com.travelplanner.trip.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WishlistService {

    private final WishlistItemRepository repository;

    public WishlistService(WishlistItemRepository repository) {
        this.repository = repository;
    }

    /** Додавання ідемпотентне: якщо місце вже у вішлісті — повертаємо наявний запис. */
    @Transactional
    public WishlistItemResponse add(UUID userId, WishlistItemRequest req) {
        WishlistItem item = repository.findByUserIdAndPlaceId(userId, req.placeId())
                .orElseGet(() -> repository.save(WishlistItem.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .placeId(req.placeId())
                        .placeName(req.placeName())
                        .placeLat(req.placeLat())
                        .placeLon(req.placeLon())
                        .note(req.note())
                        .build()));
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void remove(UUID userId, UUID placeId) {
        repository.findByUserIdAndPlaceId(userId, placeId).ifPresent(repository::delete);
    }

    private WishlistItemResponse toResponse(WishlistItem w) {
        return new WishlistItemResponse(
                w.getId(), w.getPlaceId(), w.getPlaceName(),
                w.getPlaceLat(), w.getPlaceLon(), w.getNote(), w.getCreatedAt());
    }
}
