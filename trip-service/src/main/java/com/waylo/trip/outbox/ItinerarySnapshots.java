package com.waylo.trip.outbox;

import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.OutboxEvent;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.OutboxRepository;
import com.waylo.trip.repository.TripDayRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Знімок маршруту для інших сервісів (топік trip.itinerary.snapshot).
 *
 * Event-carried state transfer: у події ПОВНИЙ стан, а не «пункт X переїхав у
 * день 3». Споживачу (context-service) не треба ні ходити назад у trip-service,
 * ні відтворювати історію змін — він просто тримає останній знімок. Тому подія
 * ідемпотентна: повторна доставка чи дубль нічого не ламають.
 *
 * Пишеться в outbox у ТІЙ САМІЙ транзакції, що й зміна маршруту
 * (Propagation.MANDATORY гарантує, що виклик поза транзакцією впаде одразу,
 * а не тихо створить подію, яка розійдеться з даними).
 */
@Component
public class ItinerarySnapshots {

    private final OutboxRepository outboxRepository;
    private final TripDayRepository tripDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final JsonMapper jsonMapper;
    private final String topic;

    public ItinerarySnapshots(OutboxRepository outboxRepository,
                              TripDayRepository tripDayRepository,
                              ItineraryItemRepository itineraryItemRepository,
                              JsonMapper jsonMapper,
                              @Value("${app.topics.itinerary-snapshot}") String topic) {
        this.outboxRepository = outboxRepository;
        this.tripDayRepository = tripDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    /** Поточний стан маршруту подорожі. Запити JPA самі флашать незбережені зміни транзакції. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Trip trip) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (TripDay day : tripDayRepository.findByTripIdOrderByDayIndexAsc(trip.getId())) {
            List<Map<String, Object>> places = new ArrayList<>();
            for (ItineraryItem item : itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(day.getId())) {
                Map<String, Object> place = new LinkedHashMap<>();
                place.put("name", item.getPlaceName());
                place.put("category", item.getPlaceCategory());
                places.add(place);
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("dayIndex", day.getDayIndex());
            d.put("date", day.getDayDate().toString());
            d.put("places", places);
            days.add(d);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", trip.getId().toString());
        payload.put("deleted", Boolean.FALSE);
        payload.put("destinationName", trip.getDestinationName());
        payload.put("lat", trip.getDestinationLat());
        payload.put("lon", trip.getDestinationLon());
        payload.put("startDate", trip.getStartDate().toString());
        payload.put("endDate", trip.getEndDate().toString());
        payload.put("snapshotAt", Instant.now().toString());
        payload.put("days", days);
        write(trip.getId(), payload);
    }

    /** Подорож видалено — споживачі мають забути її. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDeleted(UUID tripId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", tripId.toString());
        payload.put("deleted", Boolean.TRUE);
        payload.put("snapshotAt", Instant.now().toString());
        write(tripId, payload);
    }

    private void write(UUID tripId, Map<String, Object> payload) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Не вдалося серіалізувати знімок маршруту", e);
        }
        outboxRepository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Trip")
                .aggregateId(tripId)          // ключ Kafka = tripId → порядок знімків у межах подорожі
                .eventType(topic)
                .payload(json)
                .attempts(0)
                .build());
    }
}
