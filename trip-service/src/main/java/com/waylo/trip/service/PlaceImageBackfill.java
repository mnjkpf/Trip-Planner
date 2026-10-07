package com.waylo.trip.service;

import com.waylo.trip.client.PlaceClient;
import com.waylo.trip.client.PlaceClient.PlaceUnavailableException;
import com.waylo.trip.client.PlaceLookup;
import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.repository.ItineraryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Дотягує фото місць для маршрутів, спланованих до того, як planner навчився
 * передавати imageUrl разом із подією.
 *
 * Корисний і для нових: пошук у place-service повертає те, що дав провайдер POI
 * (а він фото майже ніколи не дає), тоді як GET /api/places/{id} ліниво добирає
 * його з Wikipedia і кешує. Тобто цей job — і дозаповнення старого, і спусковий
 * гачок для збагачення каталогу.
 *
 * Чому фоном, а не під час GET /itinerary: відкриття подорожі не має чекати на
 * десятки звернень до сусіднього сервісу. Користувач відкриває маршрут — фото
 * вже там або зʼявиться за хвилину.
 *
 * Транзакцій навколо мережі тут навмисно немає: читання й запис — два коротких
 * походи в БД через репозиторій, а між ними HTTP. Інакше зʼєднання з базою
 * висіло б відкритим усі ці секунди.
 */
@Service
@ConditionalOnProperty(name = "app.place-images.backfill", havingValue = "true", matchIfMissing = true)
public class PlaceImageBackfill {

    private static final Logger log = LoggerFactory.getLogger(PlaceImageBackfill.class);

    private final ItineraryItemRepository itemRepository;
    private final PlaceClient placeClient;

    public PlaceImageBackfill(ItineraryItemRepository itemRepository, PlaceClient placeClient) {
        this.itemRepository = itemRepository;
        this.placeClient = placeClient;
    }

    @Scheduled(initialDelayString = "${app.place-images.initial-delay:PT30S}",
               fixedDelayString = "${app.place-images.interval:PT5M}")
    public void backfill() {
        List<ItineraryItem> pending = itemRepository.findTop50ByImageCheckedAtIsNullOrderBySnapshotAtDesc();
        if (pending.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<ItineraryItem> updated = new ArrayList<>();
        int found = 0;
        int skipped = 0;
        for (ItineraryItem item : pending) {
            if (!isPlaceServiceId(item.getPlaceId())) {
                item.setImageCheckedAt(now);  // місце не з нашого каталогу — питати нікого
                updated.add(item);
                continue;
            }
            try {
                PlaceLookup place = placeClient.findById(item.getPlaceId()).orElse(null);
                if (place != null && place.imageUrl() != null && !place.imageUrl().isBlank()) {
                    item.setPlaceImageUrl(place.imageUrl());
                    found++;
                }
                // Позначку ставимо ЛИШЕ отримавши відповідь. Відсутність фото —
                // теж відповідь, а от збій мережі ні: інакше випадкова невдала
                // секунда залишила б місце без фото назавжди.
                item.setImageCheckedAt(now);
                updated.add(item);
            } catch (PlaceUnavailableException ex) {
                skipped++;
            }
        }
        itemRepository.saveAll(updated);
        log.info("фото місць: перевірено {}, знайдено {}, відкладено {}",
                updated.size(), found, skipped);
    }

    /** У каталозі place-service id — UUID; вручну додані місця мають інші ідентифікатори. */
    private static boolean isPlaceServiceId(String placeId) {
        if (placeId == null) {
            return false;
        }
        try {
            UUID.fromString(placeId);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
