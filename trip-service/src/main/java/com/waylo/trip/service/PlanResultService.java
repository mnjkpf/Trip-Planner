package com.waylo.trip.service;

import com.waylo.trip.consumer.PlanCompletedEvent;
import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.PlanJob;
import com.waylo.trip.domain.PlanJobStatus;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.domain.TripStatus;
import com.waylo.trip.outbox.ItinerarySnapshots;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.PlanJobRepository;
import com.waylo.trip.repository.TripDayRepository;
import com.waylo.trip.repository.TripRepository;
import com.waylo.trip.sse.PlanCompletedInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Застосовує результат планування (подію trip.plan.completed): пише дні й пункти
 * маршруту, переводить PlanJob → COMPLETED і Trip → PLANNED. Усе в ОДНІЙ транзакції.
 *
 * Ідемпотентність споживача — через власний стан у БД: якщо job уже COMPLETED,
 * подію (повторну доставку at-least-once) пропускаємо. На першій доставці job у
 * стані PENDING, тож рядків маршруту ще нема — вставляємо чисто; якщо транзакція
 * впаде, вона повністю відкотиться й повтор обробиться з нуля.
 */
@Service
public class PlanResultService {

    private static final Logger log = LoggerFactory.getLogger(PlanResultService.class);
    private static final int DEFAULT_DWELL_MINUTES = 60;

    private final PlanJobRepository planJobRepository;
    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final ApplicationEventPublisher events;
    private final ItinerarySnapshots snapshots;

    public PlanResultService(PlanJobRepository planJobRepository,
                             TripRepository tripRepository,
                             TripDayRepository tripDayRepository,
                             ItineraryItemRepository itineraryItemRepository,
                             ApplicationEventPublisher events,
                             ItinerarySnapshots snapshots) {
        this.planJobRepository = planJobRepository;
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.events = events;
        this.snapshots = snapshots;
    }

    @Transactional
    public void apply(PlanCompletedEvent event) {
        UUID jobId = UUID.fromString(event.jobId());
        PlanJob job = planJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("plan.completed для невідомого job {} — пропускаю", jobId);
            return;
        }
        if (job.getStatus() == PlanJobStatus.COMPLETED) {
            log.info("job {} уже COMPLETED — дубль trip.plan.completed пропущено", jobId);
            return;
        }

        UUID tripId = UUID.fromString(event.tripId());
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            log.warn("plan.completed для невідомої подорожі {} — пропускаю", tripId);
            return;
        }

        Instant now = Instant.now();
        int itemCount = 0;
        List<PlanCompletedEvent.Day> days = event.days() == null ? List.of() : event.days();
        for (PlanCompletedEvent.Day day : days) {
            TripDay tripDay = TripDay.builder()
                    .id(UUID.randomUUID())
                    .tripId(tripId)
                    .dayDate(day.date())
                    .dayIndex(day.dayNumber())
                    .build();
            tripDayRepository.save(tripDay);

            List<PlanCompletedEvent.Item> items = day.items() == null ? List.of() : day.items();
            for (PlanCompletedEvent.Item item : items) {
                Integer dwell = item.dwellMinutes();
                ItineraryItem entity = ItineraryItem.builder()
                        .id(UUID.randomUUID())
                        .tripDayId(tripDay.getId())
                        .placeId(item.placeId())
                        .placeName(item.name())
                        .placeCategory(item.category())
                        .placeLat(item.lat())
                        .placeLon(item.lon())
                        .placeImageUrl(item.imageUrl())
                        // Планувальник уже питав place-service, тож фонове
                        // дозаповнення цей пункт більше не чіпатиме.
                        .imageCheckedAt(now)
                        .snapshotAt(now)
                        .orderIndex(item.order())
                        .plannedStart(parseTime(item.plannedStart()))
                        .plannedEnd(parseTime(item.plannedEnd()))
                        .dwellMinutes(dwell != null && dwell > 0 ? dwell : DEFAULT_DWELL_MINUTES)
                        .travelModeFromPrev(item.plannedStart() != null ? "walk" : null)
                        .travelMinutesFromPrev(item.travelMinutesFromPrev())
                        .locked(false)
                        .build();
                itineraryItemRepository.save(entity);
                itemCount++;
            }
        }

        job.setStatus(PlanJobStatus.COMPLETED);
        job.setCompletedAt(now);
        planJobRepository.save(job);

        trip.setStatus(TripStatus.PLANNED);
        tripRepository.save(trip);

        // Новий маршрут → знімок для context-service (та сама транзакція, через outbox)
        snapshots.publish(trip);

        // SSE-підписники дізнаються ПІСЛЯ коміту (AFTER_COMMIT), щоб дані вже були в БД
        events.publishEvent(new PlanCompletedInternal(tripId));

        log.info("маршрут застосовано для подорожі {}: {} днів, {} пунктів (job {})",
                tripId, days.size(), itemCount, jobId);
    }

    /** "HH:mm" (або null) → LocalTime. */
    private static LocalTime parseTime(String s) {
        return (s == null || s.isBlank()) ? null : LocalTime.parse(s);
    }
}
