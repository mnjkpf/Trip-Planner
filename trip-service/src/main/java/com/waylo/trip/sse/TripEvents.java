package com.waylo.trip.sse;

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
 * Довгоживучий SSE-канал сторінки подорожі (на відміну від PlanEvents, що
 * закривається після «planned»). Несе короткі сигнали «перечитай»:
 *   weather-alerts — змінились погодні поради;
 *   photos         — додалось або зникло фото.
 * Самі дані клієнт бере звичайним GET, бо відповідь залежить від того, хто
 * дивиться (сховані попередження й «моє» фото — персональні).
 *
 * Останню ланку event-driven ланцюгів видно саме тут:
 *   job у context-service → Kafka → trip-service → SSE → відкрита сторінка;
 *   воркер мініатюр       → Kafka → trip-service → SSE → галерея.
 */
@Component
public class TripEvents {

    private static final Logger log = LoggerFactory.getLogger(TripEvents.class);
    /** Після таймауту клієнт просто перепідключається. */
    private static final long TIMEOUT_MS = 15 * 60_000L;

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
        try {
            // Коментар одразу віддає заголовки: клієнт бачить, що канал відкрито.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void publish(UUID tripId, String eventName) {
        List<SseEmitter> list = byTrip.get(tripId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(eventName));
            } catch (IOException | IllegalStateException ex) {
                log.debug("SSE {} для {} не доставлено: {}", eventName, tripId, ex.getMessage());
                remove(tripId, emitter);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWeatherAlertsChanged(WeatherAlertsChanged event) {
        publish(event.tripId(), "weather-alerts");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPhotosChanged(PhotosChanged event) {
        publish(event.tripId(), "photos");
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
