package com.travelplanner.trip.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Реєстр відкритих SSE-підписок за tripId. Завершення плану відбувається в цьому ж
 * сервісі (Kafka-лісенер), тож in-process реєстру достатньо. Нотифікуємо ПІСЛЯ коміту
 * транзакції (AFTER_COMMIT) — щоб на момент події маршрут уже був у БД і клієнтський
 * GET /itinerary одразу бачив дані.
 */
@Component
public class PlanEvents {

    private static final Logger log = LoggerFactory.getLogger(PlanEvents.class);
    private static final long TIMEOUT_MS = 120_000L;

    private final Map<UUID, List<SseEmitter>> byTrip = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tripId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        byTrip.computeIfAbsent(tripId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(tripId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(tripId, emitter);
        });
        emitter.onError(e -> remove(tripId, emitter));
        return emitter;
    }

    /** Надіслати «planned» і завершити всі підписки поїздки. */
    public void completed(UUID tripId) {
        List<SseEmitter> list = byTrip.remove(tripId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("planned").data("PLANNED"));
                emitter.complete();
            } catch (IOException | IllegalStateException ex) {
                log.debug("не вдалося надіслати SSE для {}: {}", tripId, ex.getMessage());
                emitter.completeWithError(ex);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlanCompleted(PlanCompletedInternal event) {
        completed(event.tripId());
    }

    private void remove(UUID tripId, SseEmitter emitter) {
        List<SseEmitter> list = byTrip.get(tripId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                byTrip.remove(tripId);
            }
        }
    }
}
