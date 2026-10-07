package com.waylo.trip.outbox;

import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripStatus;
import com.waylo.trip.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Перевидає знімки маршрутів ще не завершених подорожей на старті сервісу.
 *
 * Навіщо: подорожі, сплановані ДО появи топіка trip.itinerary.snapshot, інакше
 * ніколи б не потрапили в context-service. Знімок ідемпотентний (останній
 * перемагає), тож повторна публікація при кожному рестарті безпечна — і це
 * простіше, ніж окремий одноразовий скрипт міграції.
 */
@Component
@ConditionalOnProperty(name = "app.weather.snapshot-backfill", havingValue = "true", matchIfMissing = true)
public class SnapshotBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBackfill.class);

    private final TripRepository tripRepository;
    private final ItinerarySnapshots snapshots;

    public SnapshotBackfill(TripRepository tripRepository, ItinerarySnapshots snapshots) {
        this.tripRepository = tripRepository;
        this.snapshots = snapshots;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Trip> upcoming = tripRepository.findByStatusAndEndDateGreaterThanEqual(
                TripStatus.PLANNED, LocalDate.now());
        upcoming.forEach(snapshots::publish);
        if (!upcoming.isEmpty()) {
            log.info("знімки маршрутів перевидано для {} подорожей", upcoming.size());
        }
    }
}
