package com.waylo.context.watch;

import com.waylo.context.advice.PlannedDay;
import com.waylo.context.advice.PlannedPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Тримає проєкцію watched_trips в актуальному стані за знімками з Kafka.
 *
 * Знімок несе повний стан, тож застосування — простий upsert «останній
 * перемагає». Повторна доставка (at-least-once) нічого не ламає, а знімок,
 * старіший за збережений, відкидаємо за snapshotAt.
 */
@Service
public class TripProjection {

    private static final Logger log = LoggerFactory.getLogger(TripProjection.class);

    private final WatchedTripRepository repository;
    private final JsonMapper jsonMapper;

    public TripProjection(WatchedTripRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /** @return true, якщо знімок збережено і подорож варто перевірити. */
    @Transactional
    public boolean apply(ItinerarySnapshotEvent event) {
        if (event.tripId() == null) {
            return false;
        }
        UUID tripId = UUID.fromString(event.tripId());
        Instant at = event.snapshotAt() != null ? event.snapshotAt() : Instant.now();
        WatchedTrip existing = repository.findById(tripId).orElse(null);

        if (existing != null && existing.getSnapshotAt().isAfter(at)) {
            log.info("знімок подорожі {} застарів ({} < {}) — пропускаю", tripId, at, existing.getSnapshotAt());
            return false;
        }
        if (Boolean.TRUE.equals(event.deleted())) {
            if (existing != null) {
                repository.delete(existing);
                log.info("подорож {} видалено — нагляд припинено", tripId);
            }
            return false;
        }
        if (event.lat() == null || event.lon() == null || event.startDate() == null || event.endDate() == null) {
            log.warn("неповний знімок подорожі {} — пропускаю", tripId);
            return false;
        }

        WatchedTrip trip = existing != null ? existing : WatchedTrip.builder().tripId(tripId).build();
        trip.setDestinationName(event.destinationName());
        trip.setLat(event.lat());
        trip.setLon(event.lon());
        trip.setStartDate(event.startDate());
        trip.setEndDate(event.endDate());
        trip.setSnapshotAt(at);
        trip.setDays(jsonMapper.writeValueAsString(toPlannedDays(event.days())));
        repository.save(trip);
        log.info("знімок подорожі {} оновлено: {} – {}, днів {}", tripId,
                event.startDate(), event.endDate(), event.days() == null ? 0 : event.days().size());
        return true;
    }

    /** Дні зі збереженого знімка. */
    public List<PlannedDay> days(WatchedTrip trip) {
        if (trip.getDays() == null || trip.getDays().isBlank()) {
            return List.of();
        }
        return Arrays.asList(jsonMapper.readValue(trip.getDays(), PlannedDay[].class));
    }

    private static List<PlannedDay> toPlannedDays(List<ItinerarySnapshotEvent.Day> days) {
        List<PlannedDay> result = new ArrayList<>();
        if (days == null) {
            return result;
        }
        int position = 0;
        for (ItinerarySnapshotEvent.Day d : days) {
            position++;
            if (d == null || d.date() == null) {
                continue;
            }
            List<PlannedPlace> places = new ArrayList<>();
            if (d.places() != null) {
                for (ItinerarySnapshotEvent.Place p : d.places()) {
                    if (p != null) {
                        places.add(new PlannedPlace(p.name(), p.category()));
                    }
                }
            }
            result.add(new PlannedDay(d.dayIndex() != null ? d.dayIndex() : position, d.date(), places));
        }
        return result;
    }
}
