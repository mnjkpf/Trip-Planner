package com.waylo.trip.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.OutboxEvent;
import com.waylo.trip.domain.PlanJob;
import com.waylo.trip.domain.PlanJobStatus;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.domain.TripMember;
import com.waylo.trip.domain.TripRole;
import com.waylo.trip.domain.TripStatus;
import com.waylo.trip.access.TripAccess;
import com.waylo.trip.dto.AddItemRequest;
import com.waylo.trip.dto.CreateTripRequest;
import com.waylo.trip.dto.ItineraryResponse;
import com.waylo.trip.dto.PatchItemRequest;
import com.waylo.trip.dto.PlanJobResponse;
import com.waylo.trip.dto.PlanPreferences;
import com.waylo.trip.dto.TripResponse;
import com.waylo.trip.dto.UpdateTripRequest;
import com.waylo.trip.error.ApiExceptions.TripConflictException;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.OutboxRepository;
import com.waylo.trip.repository.PlanJobRepository;
import com.waylo.trip.repository.TripDayRepository;
import com.waylo.trip.repository.TripMemberRepository;
import com.waylo.trip.repository.TripRepository;
import com.waylo.trip.sse.PlanEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final TripAccess access;
    private final TripMemberRepository memberRepository;

    public TripService(TripRepository tripRepository,
                       PlanJobRepository planJobRepository,
                       OutboxRepository outboxRepository,
                       TripDayRepository tripDayRepository,
                       ItineraryItemRepository itineraryItemRepository,
                       PlanEvents planEvents,
                       ObjectProvider<Tracer> tracerProvider,
                       JsonMapper jsonMapper,
                       TripAccess access,
                       TripMemberRepository memberRepository) {
        this.tripRepository = tripRepository;
        this.planJobRepository = planJobRepository;
        this.outboxRepository = outboxRepository;
        this.tripDayRepository = tripDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.planEvents = planEvents;
        this.tracerProvider = tracerProvider;
        this.jsonMapper = jsonMapper;
        this.access = access;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public TripResponse create(UUID userId, String userEmail, CreateTripRequest req) {
        requireValidDates(req.startDate(), req.endDate());
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title(req.title())
                .destinationName(req.destinationName())
                .destinationCountry(req.destinationCountry())
                .destinationLat(req.destinationLat())
                .destinationLon(req.destinationLon())
                .originAirport(req.originAirport())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .status(TripStatus.DRAFT)
                .build();
        applyPreferences(trip, req.preferences());
        tripRepository.save(trip);
        // Автор одразу стає учасником: доступ скрізь перевіряється саме по
        // членству, тож без цього рядка він не побачив би власну подорож.
        memberRepository.save(TripMember.builder()
                .id(UUID.randomUUID())
                .tripId(trip.getId())
                .userId(userId)
                .email(userEmail == null ? "" : userEmail)
                .role(TripRole.OWNER)
                .invitedBy(userId)
                .build());
        return toResponse(trip, TripRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> list(UUID userId) {
        Map<UUID, TripRole> roles = memberRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(TripMember::getTripId, TripMember::getRole));
        return tripRepository.findForMember(userId).stream()
                .map(t -> toResponse(t, roles.get(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse get(UUID userId, UUID tripId) {
        Trip trip = access.require(userId, tripId, TripRole.VIEWER);
        return toResponse(trip, access.roleOf(userId, tripId));
    }

    /**
     * Редагування подорожі. Правила:
     *  - поки статус PLANNING (async-петля в польоті) — редагувати не можна (409),
     *    інакше planner перезапише маршрут поверх нових даних;
     *  - якщо змінилися ВХІДНІ ДЛЯ ПЛАНУВАННЯ поля (дати або координати призначення)
     *    і маршрут уже побудований — скидаємо його (дні+пункти) і повертаємо статус
     *    у DRAFT, щоб користувач переспланував під нові вхідні. Косметичні зміни
     *    (назва, країна) статус не чіпають.
     */
    @Transactional
    public TripResponse update(UUID userId, UUID tripId, UpdateTripRequest req) {
        Trip trip = access.require(userId, tripId, TripRole.EDITOR);
        if (trip.getStatus() == TripStatus.PLANNING) {
            throw new TripConflictException("Подорож зараз планується — зачекай завершення, перш ніж редагувати");
        }
        requireValidDates(req.startDate(), req.endDate());

        boolean planningInputsChanged =
                !trip.getStartDate().equals(req.startDate())
                        || !trip.getEndDate().equals(req.endDate())
                        || trip.getDestinationLat() != req.destinationLat()
                        || trip.getDestinationLon() != req.destinationLon();

        trip.setTitle(req.title());
        trip.setDestinationName(req.destinationName());
        trip.setDestinationCountry(req.destinationCountry());
        trip.setDestinationLat(req.destinationLat());
        trip.setDestinationLon(req.destinationLon());
        trip.setOriginAirport(req.originAirport());
        trip.setStartDate(req.startDate());
        trip.setEndDate(req.endDate());
        replacePreferences(trip, req.preferences());

        if (planningInputsChanged && trip.getStatus() == TripStatus.PLANNED) {
            clearItinerary(tripId);
            trip.setStatus(TripStatus.DRAFT);
        }
        // managed entity — зміни підуть dirty checking-ом на комміті
        return toResponse(trip, access.roleOf(userId, tripId));
    }

    /**
     * Готовий маршрут: дні по порядку, у кожному — пункти по порядку.
     * Доступ — через TripAccess: маршрут бачать усі учасники, і глядачі теж.
     */
    @Transactional(readOnly = true)
    public ItineraryResponse itinerary(UUID userId, UUID tripId) {
        Trip trip = access.require(userId, tripId, TripRole.VIEWER);
        List<ItineraryResponse.Day> days = tripDayRepository.findByTripIdOrderByDayIndexAsc(tripId).stream()
                .map(this::toDay)
                .toList();
        return new ItineraryResponse(tripId, trip.getStatus(), days);
    }

    /** Один день маршруту: пункти по порядку + дистанція пішки (haversine) і хвилини ходьби. */
    private ItineraryResponse.Day toDay(TripDay d) {
        List<ItineraryItem> rows = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(d.getId());
        List<ItineraryResponse.Item> items = new ArrayList<>();
        double meters = 0;
        int walk = 0;
        ItineraryItem prev = null;
        for (ItineraryItem i : rows) {
            if (prev != null) {
                meters += haversine(prev.getPlaceLat(), prev.getPlaceLon(), i.getPlaceLat(), i.getPlaceLon());
            }
            if (i.getTravelMinutesFromPrev() != null) {
                walk += i.getTravelMinutesFromPrev();
            }
            items.add(new ItineraryResponse.Item(
                    i.getId(), i.getOrderIndex(), i.getPlaceId(), i.getPlaceName(), i.getPlaceCategory(),
                    i.getPlaceLat(), i.getPlaceLon(),
                    fmtTime(i.getPlannedStart()), i.getDwellMinutes(), i.getTravelMinutesFromPrev(),
                    i.isLocked(), i.getNote()));
            prev = i;
        }
        double km = Math.round(meters / 100.0) / 10.0;   // 0.1 км
        return new ItineraryResponse.Day(d.getDayIndex(), d.getDayDate(), km, walk, items);
    }

    private static String fmtTime(java.time.LocalTime t) {
        return t == null ? null : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /** Відстань по великому колу в метрах. */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(x));
    }

    @Transactional
    public void delete(UUID userId, UUID tripId) {
        Trip trip = access.require(userId, tripId, TripRole.OWNER);
        tripRepository.delete(trip);   // trip_days / itinerary_items / plan_jobs — ON DELETE CASCADE
    }

    /**
     * Запит на побудову маршруту. КЛЮЧОВИЙ метод: PlanJob і подія outbox
     * пишуться в ОДНУ транзакцію. Клієнт одразу отримує 202 з jobId, а
     * planner-service підхопить подію з Kafka (її туди відправить
     * OutboxPublisher). Дві бази ніколи не розходяться.
     */
    @Transactional
    public PlanJobResponse requestPlan(UUID userId, UUID tripId) {
        Trip trip = access.require(userId, tripId, TripRole.EDITOR);

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
        Trip trip = access.require(userId, tripId, TripRole.VIEWER);
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

    private void requireValidDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Дата кінця не може бути раніше за дату початку");
        }
    }

    /**
     * Прибирає побудований маршрут (дні + пункти) поточної подорожі. Викликається
     * при зміні вхідних для планування полів, щоб не лишати застарілий маршрут.
     * Пункти видаляємо явно (по днях), потім самі дні.
     */
    private void clearItinerary(UUID tripId) {
        List<TripDay> days = tripDayRepository.findByTripIdOrderByDayIndexAsc(tripId);
        for (TripDay d : days) {
            List<ItineraryItem> items = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(d.getId());
            itineraryItemRepository.deleteAll(items);
        }
        tripDayRepository.deleteAll(days);
    }

    /**
     * Переносить побажання з DTO в сутність. null-поля не чіпаємо — так можна
     * надіслати лише одну зміну, не стираючи решту; явний порожній список
     * інтересів стирає попередній вибір.
     */
    private void applyPreferences(Trip t, PlanPreferences p) {
        if (p == null) return;
        if (p.pace() != null) t.setPace(p.pace());
        if (p.interests() != null) {
            t.setInterests(p.interests().isEmpty() ? null : String.join(",", p.interests()));
        }
        if (p.searchRadiusM() != null) t.setSearchRadiusM(p.searchRadiusM());
        if (p.dayStartTime() != null) t.setDayStartTime(p.dayStartTime());
    }

    /**
     * PUT — повна заміна: усе, що прийшло null, СТИРАЄТЬСЯ (так користувач може
     * зняти раніше вибраний фільтр). Якщо поля preferences у запиті немає взагалі
     * (старий клієнт), побажання лишаємо як були.
     */
    private void replacePreferences(Trip t, PlanPreferences p) {
        if (p == null) return;
        t.setPace(p.pace());
        t.setInterests(p.interests() == null || p.interests().isEmpty()
                ? null : String.join(",", p.interests()));
        t.setSearchRadiusM(p.searchRadiusM());
        t.setDayStartTime(p.dayStartTime());
    }

    private PlanPreferences readPreferences(Trip t) {
        List<String> interests = (t.getInterests() == null || t.getInterests().isBlank())
                ? List.of()
                : List.of(t.getInterests().split(","));
        return new PlanPreferences(t.getPace(), interests, t.getSearchRadiusM(), t.getDayStartTime());
    }

    private TripResponse toResponse(Trip t, TripRole role) {
        return new TripResponse(
                t.getId(), t.getUserId(), t.getTitle(),
                t.getDestinationName(), t.getDestinationCountry(),
                t.getDestinationLat(), t.getDestinationLon(),
                t.getOriginAirport(),
                t.getStartDate(), t.getEndDate(), t.getStatus(),
                readPreferences(t), role, t.getCreatedAt());
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
        // Побажання до планування — усі опційні, planner має свої дефолти на null.
        if (trip.getPace() != null) payload.put("pace", trip.getPace());
        if (trip.getInterests() != null && !trip.getInterests().isBlank()) {
            payload.put("interests", trip.getInterests());
        }
        if (trip.getSearchRadiusM() != null) payload.put("searchRadiusM", trip.getSearchRadiusM());
        if (trip.getDayStartTime() != null) payload.put("dayStartTime", trip.getDayStartTime().toString());
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

    // ───────────────────────── Редагування маршруту ─────────────────────────

    /** Додати місце (з вішлісту) у кінець дня; перенумерувати й перерахувати час дня. */
    @Transactional
    public ItineraryResponse addItem(UUID userId, UUID tripId, int dayIndex, AddItemRequest req) {
        access.require(userId, tripId, TripRole.EDITOR);
        TripDay day = tripDayRepository.findByTripIdAndDayIndex(tripId, dayIndex)
                .orElseThrow(() -> new TripNotFoundException("День не знайдено: " + dayIndex));
        List<ItineraryItem> list = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(day.getId());
        ItineraryItem item = ItineraryItem.builder()
                .id(UUID.randomUUID())
                .tripDayId(day.getId())
                .placeId(req.placeId())
                .placeName(req.placeName())
                .placeCategory(req.category())
                .placeLat(req.lat())
                .placeLon(req.lon())
                .snapshotAt(Instant.now())
                .orderIndex(list.size() + 1)
                .dwellMinutes(dwellFor(req.category()))
                .locked(false)
                .build();
        itineraryItemRepository.save(item);
        list.add(item);
        renumberAndReschedule(list);
        return itinerary(userId, tripId);
    }

    @Transactional
    public ItineraryResponse removeItem(UUID userId, UUID tripId, UUID itemId) {
        access.require(userId, tripId, TripRole.EDITOR);
        ItineraryItem item = requireItem(tripId, itemId);
        List<ItineraryItem> list = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(item.getTripDayId());
        list.removeIf(x -> x.getId().equals(itemId));
        itineraryItemRepository.delete(item);
        renumberAndReschedule(list);
        return itinerary(userId, tripId);
    }

    /** Перенести пункт у день toDayIndex на позицію toOrder; перенумерувати обидва дні. */
    @Transactional
    public ItineraryResponse moveItem(UUID userId, UUID tripId, UUID itemId, int toDayIndex, int toOrder) {
        access.require(userId, tripId, TripRole.EDITOR);
        ItineraryItem moved = requireItem(tripId, itemId);
        UUID fromDayId = moved.getTripDayId();
        TripDay toDay = tripDayRepository.findByTripIdAndDayIndex(tripId, toDayIndex)
                .orElseThrow(() -> new TripNotFoundException("День не знайдено: " + toDayIndex));
        UUID toDayId = toDay.getId();

        if (fromDayId.equals(toDayId)) {
            List<ItineraryItem> list = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(fromDayId);
            list.removeIf(x -> x.getId().equals(itemId));
            list.add(clamp(toOrder - 1, 0, list.size()), moved);
            renumberAndReschedule(list);
        } else {
            List<ItineraryItem> fromList = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(fromDayId);
            List<ItineraryItem> toList = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(toDayId);
            fromList.removeIf(x -> x.getId().equals(itemId));
            moved.setTripDayId(toDayId);
            toList.add(clamp(toOrder - 1, 0, toList.size()), moved);
            renumberAndReschedule(fromList);
            renumberAndReschedule(toList);
        }
        return itinerary(userId, tripId);
    }

    /** Час/нотатка/locked. null = не чіпати; порожній рядок = очистити. Час не перераховуємо. */
    @Transactional
    public ItineraryResponse patchItem(UUID userId, UUID tripId, UUID itemId, PatchItemRequest req) {
        access.require(userId, tripId, TripRole.EDITOR);
        ItineraryItem item = requireItem(tripId, itemId);
        if (req.plannedStart() != null) {
            item.setPlannedStart(req.plannedStart().isBlank() ? null : LocalTime.parse(req.plannedStart()));
        }
        if (req.note() != null) {
            item.setNote(req.note().isBlank() ? null : req.note());
        }
        if (req.locked() != null) {
            item.setLocked(req.locked());
        }
        return itinerary(userId, tripId);
    }

    /** Переупорядкувати день nearest-neighbor; прибиті (locked) лишаються на місці. */
    @Transactional
    public ItineraryResponse optimizeDay(UUID userId, UUID tripId, int dayIndex) {
        access.require(userId, tripId, TripRole.EDITOR);
        TripDay day = tripDayRepository.findByTripIdAndDayIndex(tripId, dayIndex)
                .orElseThrow(() -> new TripNotFoundException("День не знайдено: " + dayIndex));
        List<ItineraryItem> list = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(day.getId());
        renumberAndReschedule(optimizeOrder(list));
        return itinerary(userId, tripId);
    }

    // ---- edit helpers ----

    private ItineraryItem requireItem(UUID tripId, UUID itemId) {
        ItineraryItem item = itineraryItemRepository.findById(itemId)
                .orElseThrow(() -> new TripNotFoundException("Пункт маршруту не знайдено: " + itemId));
        TripDay day = tripDayRepository.findById(item.getTripDayId())
                .orElseThrow(() -> new TripNotFoundException("День не знайдено"));
        if (!day.getTripId().equals(tripId)) {
            throw new TripNotFoundException("Пункт не належить цій подорожі");
        }
        return item;
    }

    /** День з 09:00: порядок 1..n, час пішки між точками (haversine), перебування за категорією. */
    private void renumberAndReschedule(List<ItineraryItem> ordered) {
        LocalTime cursor = LocalTime.of(9, 0);
        ItineraryItem prev = null;
        int idx = 1;
        for (ItineraryItem it : ordered) {
            it.setOrderIndex(idx++);
            int travel = prev == null ? 0
                    : walkMinutes(haversine(prev.getPlaceLat(), prev.getPlaceLon(), it.getPlaceLat(), it.getPlaceLon()));
            it.setTravelMinutesFromPrev(travel);
            it.setTravelModeFromPrev(prev == null ? null : "walk");
            LocalTime start = cursor.plusMinutes(travel);
            int dwell = it.getDwellMinutes() > 0 ? it.getDwellMinutes() : 60;
            it.setPlannedStart(start);
            it.setPlannedEnd(start.plusMinutes(dwell));
            cursor = start.plusMinutes(dwell);
            prev = it;
        }
    }

    private List<ItineraryItem> optimizeOrder(List<ItineraryItem> items) {
        int n = items.size();
        ItineraryItem[] result = new ItineraryItem[n];
        List<ItineraryItem> unlocked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItineraryItem it = items.get(i);
            if (it.isLocked()) {
                result[i] = it;
            } else {
                unlocked.add(it);
            }
        }
        List<ItineraryItem> nn = nearestNeighborItems(unlocked);
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (result[i] == null) {
                result[i] = nn.get(k++);
            }
        }
        return new ArrayList<>(java.util.Arrays.asList(result));
    }

    private List<ItineraryItem> nearestNeighborItems(List<ItineraryItem> pts) {
        if (pts.size() <= 2) {
            return new ArrayList<>(pts);
        }
        List<ItineraryItem> remaining = new ArrayList<>(pts);
        List<ItineraryItem> route = new ArrayList<>();
        ItineraryItem cur = remaining.remove(0);
        route.add(cur);
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                double dd = haversine(cur.getPlaceLat(), cur.getPlaceLon(),
                        remaining.get(i).getPlaceLat(), remaining.get(i).getPlaceLon());
                if (dd < bestD) {
                    bestD = dd;
                    best = i;
                }
            }
            cur = remaining.remove(best);
            route.add(cur);
        }
        return route;
    }

    private static int dwellFor(String category) {
        if (category == null) {
            return 60;
        }
        return switch (category) {
            case "MUSEUM" -> 120;
            case "RESTAURANT" -> 90;
            case "CAFE" -> 45;
            case "PARK" -> 75;
            case "HOTEL" -> 30;
            default -> 60;
        };
    }

    private static int walkMinutes(double meters) {
        if (meters <= 0) {
            return 0;
        }
        int m = (int) Math.round(meters / 1000.0 * 60.0 / 4.5);
        return Math.max(m, 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
