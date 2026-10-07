package com.waylo.trip.web;

import com.waylo.trip.dto.AddItemRequest;
import com.waylo.trip.dto.ItineraryResponse;
import com.waylo.trip.dto.MoveItemRequest;
import com.waylo.trip.dto.PatchItemRequest;
import com.waylo.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ручне редагування готового маршруту. Усі методи повертають оновлений
 * ItineraryResponse, щоб фронт одразу перемалював день. userId — із X-User-Id
 * (ставить gateway), власника перевіряє сервіс.
 */
@RestController
@RequestMapping("/api/trips/{tripId}")
public class ItineraryController {

    private final TripService tripService;

    public ItineraryController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping("/days/{dayIndex}/items")
    public ItineraryResponse add(@RequestHeader("X-User-Id") UUID userId,
                                 @PathVariable UUID tripId,
                                 @PathVariable int dayIndex,
                                 @Valid @RequestBody AddItemRequest req) {
        return tripService.addItem(userId, tripId, dayIndex, req);
    }

    @DeleteMapping("/items/{itemId}")
    public ItineraryResponse remove(@RequestHeader("X-User-Id") UUID userId,
                                    @PathVariable UUID tripId,
                                    @PathVariable UUID itemId) {
        return tripService.removeItem(userId, tripId, itemId);
    }

    @PutMapping("/items/{itemId}/move")
    public ItineraryResponse move(@RequestHeader("X-User-Id") UUID userId,
                                  @PathVariable UUID tripId,
                                  @PathVariable UUID itemId,
                                  @RequestBody MoveItemRequest req) {
        return tripService.moveItem(userId, tripId, itemId, req.toDayIndex(), req.toOrder());
    }

    @PatchMapping("/items/{itemId}")
    public ItineraryResponse patch(@RequestHeader("X-User-Id") UUID userId,
                                   @PathVariable UUID tripId,
                                   @PathVariable UUID itemId,
                                   @RequestBody PatchItemRequest req) {
        return tripService.patchItem(userId, tripId, itemId, req);
    }

    @PostMapping("/days/{dayIndex}/optimize")
    public ItineraryResponse optimize(@RequestHeader("X-User-Id") UUID userId,
                                      @PathVariable UUID tripId,
                                      @PathVariable int dayIndex) {
        return tripService.optimizeDay(userId, tripId, dayIndex);
    }
}
