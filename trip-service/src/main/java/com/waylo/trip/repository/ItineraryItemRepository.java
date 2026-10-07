package com.waylo.trip.repository;

import com.waylo.trip.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    /** Пункти одного дня у порядку їхнього маршруту. */
    List<ItineraryItem> findByTripDayIdOrderByOrderIndexAsc(UUID tripDayId);

    /** Усі пункти, що посилаються на placeId (використовується рідко, напр. для сповіщень). */
    List<ItineraryItem> findByPlaceId(String placeId);

    /** Пункти, для яких фото ще не шукали — найсвіжіші маршрути першими. */
    List<ItineraryItem> findTop50ByImageCheckedAtIsNullOrderBySnapshotAtDesc();
}
