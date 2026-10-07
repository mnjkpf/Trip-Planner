package com.waylo.context.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Споживач trip.itinerary.snapshot: оновлює проєкцію й одразу перевіряє подорож.
 * Тож якщо користувач переставив парк на дощовий день, попередження з'явиться
 * за секунди, а не на наступному плановому проході.
 */
@Component
public class ItinerarySnapshotListener {

    private static final Logger log = LoggerFactory.getLogger(ItinerarySnapshotListener.class);

    private final JsonMapper jsonMapper;
    private final TripProjection projection;
    private final WeatherWatchService watchService;

    public ItinerarySnapshotListener(JsonMapper jsonMapper,
                                     TripProjection projection,
                                     WeatherWatchService watchService) {
        this.jsonMapper = jsonMapper;
        this.projection = projection;
        this.watchService = watchService;
    }

    @KafkaListener(topics = "${app.topics.itinerary-snapshot}")
    public void onSnapshot(String payload) {
        ItinerarySnapshotEvent event = jsonMapper.readValue(payload, ItinerarySnapshotEvent.class);
        if (!projection.apply(event)) {
            return;
        }
        try {
            watchService.checkTrip(UUID.fromString(event.tripId()), LocalDate.now());
        } catch (RuntimeException ex) {
            // знімок уже збережено — перевірку повторить плановий job, ретраїти подію не треба
            log.warn("перевірка погоди для {} не вдалась: {}", event.tripId(), ex.getMessage());
        }
    }
}
