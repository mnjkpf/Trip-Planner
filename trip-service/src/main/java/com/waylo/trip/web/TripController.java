package com.waylo.trip.web;

import com.waylo.trip.dto.CreateTripRequest;
import com.waylo.trip.dto.ItineraryResponse;
import com.waylo.trip.dto.PlanJobResponse;
import com.waylo.trip.dto.TripResponse;
import com.waylo.trip.dto.UpdateTripRequest;
import com.waylo.trip.export.CalendarService;
import com.waylo.trip.export.IcsExporter;
import com.waylo.trip.export.IcsResponse;
import com.waylo.trip.service.TripService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final CalendarService calendarService;
    private final IcsExporter icsExporter;

    public TripController(TripService tripService,
                          CalendarService calendarService,
                          IcsExporter icsExporter) {
        this.tripService = tripService;
        this.calendarService = calendarService;
        this.icsExporter = icsExporter;
    }

    /** Маршрут як файл календаря — імпортується в Google Calendar, Apple Calendar, Outlook. */
    @GetMapping("/{id}/calendar.ics")
    public ResponseEntity<byte[]> calendar(@RequestHeader("X-User-Id") UUID userId,
                                           @PathVariable UUID id) {
        return IcsResponse.attachment(icsExporter, calendarService.forOwner(userId, id));
    }

    // userId у всіх методах — з заголовка X-User-Id, який ставить gateway
    // з валідованого токена. Немає заголовка → 401 (GlobalExceptionHandler).

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@RequestHeader("X-User-Id") UUID userId,
                               @Valid @RequestBody CreateTripRequest req) {
        return tripService.create(userId, req);
    }

    @GetMapping
    public List<TripResponse> list(@RequestHeader("X-User-Id") UUID userId) {
        return tripService.list(userId);
    }

    @GetMapping("/{id}")
    public TripResponse get(@RequestHeader("X-User-Id") UUID userId,
                            @PathVariable UUID id) {
        return tripService.get(userId, id);
    }

    // Редагування подорожі. Зміна дат/координат у вже спланованої скидає маршрут
    // (сервіс поверне статус у DRAFT) — деталі в TripService.update.
    @PutMapping("/{id}")
    public TripResponse update(@RequestHeader("X-User-Id") UUID userId,
                               @PathVariable UUID id,
                               @Valid @RequestBody UpdateTripRequest req) {
        return tripService.update(userId, id, req);
    }

    // Готовий маршрут (дні + пункти). Фронт тягне його після статусу PLANNED.
    @GetMapping("/{id}/itinerary")
    public ItineraryResponse itinerary(@RequestHeader("X-User-Id") UUID userId,
                                       @PathVariable UUID id) {
        return tripService.itinerary(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-User-Id") UUID userId,
                       @PathVariable UUID id) {
        tripService.delete(userId, id);
    }

    // SSE-потік статусу планування: подія 'planned', коли маршрут готовий.
    @GetMapping(value = "/{id}/plan-events", produces = "text/event-stream")
    public SseEmitter planEvents(@RequestHeader("X-User-Id") UUID userId,
                                 @PathVariable UUID id) {
        return tripService.planEvents(userId, id);
    }

    // Асинхронна побудова маршруту: 202 Accepted + jobId одразу.
    @PostMapping("/{id}/plan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PlanJobResponse requestPlan(@RequestHeader("X-User-Id") UUID userId,
                                       @PathVariable UUID id) {
        return tripService.requestPlan(userId, id);
    }
}
