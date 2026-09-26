package com.travelplanner.trip.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.travelplanner.trip.domain.OutboxEvent;
import com.travelplanner.trip.domain.PlanJob;
import com.travelplanner.trip.domain.PlanJobStatus;
import com.travelplanner.trip.domain.Trip;
import com.travelplanner.trip.domain.TripStatus;
import com.travelplanner.trip.dto.CreateTripRequest;
import com.travelplanner.trip.dto.ItineraryResponse;
import com.travelplanner.trip.dto.PlanJobResponse;
import com.travelplanner.trip.dto.TripResponse;
import com.travelplanner.trip.error.ApiExceptions.TripNotFoundException;
import com.travelplanner.trip.repository.ItineraryItemRepository;
import com.travelplanner.trip.repository.OutboxRepository;
import com.travelplanner.trip.repository.PlanJobRepository;
import com.travelplanner.trip.repository.TripDayRepository;
import com.travelplanner.trip.repository.TripRepository;
import com.travelplanner.trip.sse.PlanEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final PlanJobRepository planJobRepository;
    private final OutboxRepository outboxRepository;
    private final TripDayRepository tripDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final PlanEvents planEvents;
    private final ObjectProvider<Tracer> tracerProvider;
    private final JsonMapper jsonMapper;

    public TripService(TripRepository tripRepository,
                       PlanJobRepository planJobRepository,
                       OutboxRepository outboxRepository,
                       TripDayRepository tripDayRepository,
                       ItineraryItemRepository itineraryItemRepository,
                       PlanEvents planEvents,
                       ObjectProvider<Tracer> tracerProvider,
                       JsonMapper jsonMapper) {
        this.tripRepository = tripRepository;
        this.planJobRepository = planJobRepository;
        this.outboxRepository = outboxRepository;
        this.tripDayRepository = tripDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.planEvents = planEvents;
        this.tracerProvider = tracerProvider;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public TripResponse create(UUID userId, CreateTripRequest req) {
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title(req.title())
                .destinationName(req.destinationName())
                .destinationCountry(req.destinationCountry())
                .destinationLat(req.destinationLat())
                .destinationLon(req.destinationLon())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .status(TripStatus.DRAFT)
                .build();
        tripRepository.save(trip);
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> list(UUID userId) {
        return tripRepository.findByUserIdOrderByStartDateDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse get(UUID userId, UUID tripId) {
        return toResponse(requireOwned(userId, tripId));
    }

    /**
     * Готовий маршрут: дні по порядку, у кожному — пункти по порядку.
     * Перевірка власника — через requireOwned, щоб не віддати чужий маршрут.
     */
    @Transactional(readOnly = true)
    public ItineraryResponse itinerary(UUID userId, UUID tripId) {
        Trip trip = requireOwned(userId, tripId);
        List<ItineraryResponse.Day> days = tripDayRepository.findByTripIdOrderByDayIndexAsc(tripId).stream()
                .map(d -> new ItineraryResponse.Day(
                        d.getDayIndex(),
                        d.getDayDate(),
                        itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(d.getId()).stream()
                                .map(i -> new ItineraryResponse.Item(
                                        i.getOrderIndex(),
                                        i.getPlaceId(),
                                        i.getPlaceName(),
                                        i.getPlaceCategory(),
                                        i.getPlaceLat(),
                                        i.getPlaceLon()))
                                .toList()))
                .toList();
        return new ItineraryResponse(tripId, trip.getStatus(), days);
    }

    @Transactional
    public void delete(UUID userId, UUID tripId) {
        Trip trip = requireOwned(userId, tripId);
        tripRepository.delete(trip);
    }

    /**
     * Запит на побудову маршруту. КЛЮЧОВИЙ метод: PlanJob і подія outbox
     * пишуться в ОДНУ транзакцію. Клієнт одразу отримує 202 з jobId, а
     * planner-service підхопить подію з Kafka (її туди відправить
     * OutboxPublisher). Дві бази ніколи не розходяться.
     */
    @Transactional
    public PlanJobResponse requestPlan(UUID userId, UUID tripId) {
        Trip trip = requireOwned(userId, tripId);

        PlanJob job = PlanJob.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .status(PlanJobStatus.PENDING)
                .requestedAt(Instant.now())
                .build();
        planJobRepository.save(job);

        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Trip")
                .aggregateId(tripId)
                .eventType("trip.plan.requested")
                .payload(toJson(buildPlanPayload(job, trip, userId)))
                .headers(traceHeaders())
                .attempts(0)
                .build();
        outboxRepository.save(event);

        trip.setStatus(TripStatus.PLANNING);   // managed entity — оновиться dirty checking

        return new PlanJobResponse(job.getId(), tripId, job.getStatus(), job.getRequestedAt());
    }

    /**
     * SSE-підписка на завершення планування. Якщо поїздка вже PLANNED — шлемо подію
     * одразу (клієнт міг підписатись після завершення). Інакше емітер чекає нотифікації
     * від PlanResultService (AFTER_COMMIT).
     */
    @Transactional(readOnly = true)
    public SseEmitter planEvents(UUID userId, UUID tripId) {
        Trip trip = requireOwned(userId, tripId);
        SseEmitter emitter = planEvents.subscribe(tripId);
        if (trip.getStatus() == TripStatus.PLANNED) {
            try {
                emitter.send(SseEmitter.event().name("planned").data("PLANNED"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    // ---- helpers ----

    private Trip requireOwned(UUID userId, UUID tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено: " + tripId));
    }

    private TripResponse toResponse(Trip t) {
        return new TripResponse(
                t.getId(), t.getUserId(), t.getTitle(),
                t.getDestinationName(), t.getDestinationCountry(),
                t.getDestinationLat(), t.getDestinationLon(),
                t.getStartDate(), t.getEndDate(), t.getStatus(), t.getCreatedAt());
    }

    /**
     * Payload події trip.plan.requested. Несе достатньо контексту, щоб planner-service
     * міг зробити роботу БЕЗ звернень назад у trip-service (event-carried state transfer):
     * координати призначення для пошуку POI і дати для розміру маршруту.
     */
    private Map<String, Object> buildPlanPayload(PlanJob job, Trip trip, UUID userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", job.getId().toString());
        payload.put("tripId", trip.getId().toString());
        payload.put("userId", userId.toString());
        payload.put("destinationName", trip.getDestinationName());
        payload.put("destinationLat", trip.getDestinationLat());
        payload.put("destinationLon", trip.getDestinationLon());
        payload.put("startDate", trip.getStartDate().toString());
        payload.put("endDate", trip.getEndDate().toString());
        return payload;
    }

    /**
     * Знімок поточного trace-контексту (W3C traceparent). Кладемо в outbox.headers,
     * щоб OutboxPublisher відновив його при публікації — тоді HTTP-запит /plan і вся
     * async-петля (planner -> context/place -> trip) будуть ОДНИМ трейсом.
     */
    private String traceHeaders() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext ctx = span.context();
        String traceparent = "00-" + ctx.traceId() + "-" + ctx.spanId() + "-01";
        return toJson(Map.of("traceparent", traceparent));
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Не вдалося серіалізувати payload outbox", e);
        }
    }
}
