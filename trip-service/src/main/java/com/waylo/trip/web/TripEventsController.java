package com.waylo.trip.web;

import com.waylo.trip.access.TripAccess;
import com.waylo.trip.domain.TripRole;
import com.waylo.trip.sse.TripEvents;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/** Живі події сторінки подорожі (див. TripEvents). Підписатись може будь-який учасник. */
@RestController
public class TripEventsController {

    private final TripAccess access;
    private final TripEvents tripEvents;

    public TripEventsController(TripAccess access, TripEvents tripEvents) {
        this.access = access;
        this.tripEvents = tripEvents;
    }

    @GetMapping(value = "/api/trips/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        access.require(userId, id, TripRole.VIEWER);
        return tripEvents.subscribe(id);
    }
}
