package com.travelplanner.trip.web;

import com.travelplanner.trip.dto.CreateTripRequest;
import com.travelplanner.trip.dto.PlanJobResponse;
import com.travelplanner.trip.dto.TripResponse;
import com.travelplanner.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public TripController(TripService tripService) {
        this.tripService = tripService;
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-User-Id") UUID userId,
                       @PathVariable UUID id) {
        tripService.delete(userId, id);
    }

    // Асинхронна побудова маршруту: 202 Accepted + jobId одразу.
    @PostMapping("/{id}/plan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PlanJobResponse requestPlan(@RequestHeader("X-User-Id") UUID userId,
                                       @PathVariable UUID id) {
        return tripService.requestPlan(userId, id);
    }
}
