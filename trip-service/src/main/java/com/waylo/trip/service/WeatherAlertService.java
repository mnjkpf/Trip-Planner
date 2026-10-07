package com.waylo.trip.service;

import com.waylo.trip.access.TripAccess;
import com.waylo.trip.consumer.WeatherAlertEvent;
import com.waylo.trip.domain.TripRole;
import com.waylo.trip.domain.WeatherAlert;
import com.waylo.trip.domain.WeatherAlertDismissal;
import com.waylo.trip.domain.WeatherAlertLevel;
import com.waylo.trip.dto.WeatherAlertResponse;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.TripRepository;
import com.waylo.trip.repository.WeatherAlertDismissalRepository;
import com.waylo.trip.repository.WeatherAlertRepository;
import com.waylo.trip.sse.WeatherAlertsChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Погодні попередження подорожі. Сервіс лише ЗБЕРІГАЄ й ПОКАЗУЄ поради від
 * context-service — маршрут він не чіпає: переставити дні чи ні, вирішує людина.
 */
@Service
public class WeatherAlertService {

    private static final Logger log = LoggerFactory.getLogger(WeatherAlertService.class);

    private final WeatherAlertRepository alertRepository;
    private final WeatherAlertDismissalRepository dismissalRepository;
    private final TripRepository tripRepository;
    private final TripAccess access;
    private final ApplicationEventPublisher events;

    public WeatherAlertService(WeatherAlertRepository alertRepository,
                               WeatherAlertDismissalRepository dismissalRepository,
                               TripRepository tripRepository,
                               TripAccess access,
                               ApplicationEventPublisher events) {
        this.alertRepository = alertRepository;
        this.dismissalRepository = dismissalRepository;
        this.tripRepository = tripRepository;
        this.access = access;
        this.events = events;
    }

    /**
     * Застосувати новий набір порад. Подія несе ПОВНИЙ стан, тож це заміна:
     *  - день є і в старому, і в новому наборі з тим самим днем для обміну —
     *    оновлюємо цифри, а відмітки «сховано» лишаються (порада та сама);
     *  - порада для дня зникла або змінилась — рядок видаляємо разом із
     *    відмітками, і змінена порада знову стане видимою всім.
     * Повторна доставка тієї ж події дає той самий результат (ідемпотентно).
     */
    @Transactional
    public void apply(WeatherAlertEvent event) {
        if (event.tripId() == null) {
            return;
        }
        UUID tripId = UUID.fromString(event.tripId());
        if (!tripRepository.existsById(tripId)) {
            log.info("погодна порада для невідомої подорожі {} — пропускаю", tripId);
            return;
        }
        Instant generatedAt = event.generatedAt() != null ? event.generatedAt() : Instant.now();
        List<WeatherAlert> existing = alertRepository.findByTripId(tripId);
        if (existing.stream().anyMatch(a -> a.getGeneratedAt().isAfter(generatedAt))) {
            log.info("погодна порада для {} застаріла — пропускаю", tripId);
            return;
        }

        Map<LocalDate, WeatherAlertEvent.Alert> incoming = new LinkedHashMap<>();
        if (event.alerts() != null) {
            for (WeatherAlertEvent.Alert a : event.alerts()) {
                if (a != null && a.date() != null) {
                    incoming.put(a.date(), a);
                }
            }
        }

        for (WeatherAlert old : existing) {
            WeatherAlertEvent.Alert fresh = incoming.get(old.getDayDate());
            if (fresh != null && Objects.equals(fresh.swapDate(), old.getSwapDate())) {
                fill(old, fresh, generatedAt);          // managed — збережеться на коміті
                incoming.remove(old.getDayDate());
            } else {
                alertRepository.delete(old);
            }
        }
        // Hibernate виконує INSERT раніше за DELETE — без flush новий рядок на той
        // самий день упав би на unique (trip_id, day_date).
        alertRepository.flush();

        for (WeatherAlertEvent.Alert fresh : incoming.values()) {
            WeatherAlert alert = new WeatherAlert();
            alert.setId(UUID.randomUUID());
            alert.setTripId(tripId);
            fill(alert, fresh, generatedAt);
            alertRepository.save(alert);
        }

        // Відкриті сторінки подорожі дізнаються через SSE — після коміту.
        events.publishEvent(new WeatherAlertsChanged(tripId));
        log.info("погодні попередження подорожі {} оновлено: {}", tripId,
                event.alerts() == null ? 0 : event.alerts().size());
    }

    /** Актуальні попередження, крім тих, що цей учасник уже сховав. */
    @Transactional(readOnly = true)
    public List<WeatherAlertResponse> list(UUID userId, UUID tripId) {
        access.require(userId, tripId, TripRole.VIEWER);
        List<WeatherAlert> alerts = alertRepository
                .findByTripIdAndDayDateGreaterThanEqualOrderByDayDateAsc(tripId, LocalDate.now());
        if (alerts.isEmpty()) {
            return List.of();
        }
        Set<UUID> hidden = dismissalRepository
                .findByUserIdAndAlertIdIn(userId, alerts.stream().map(WeatherAlert::getId).toList())
                .stream()
                .map(WeatherAlertDismissal::getAlertId)
                .collect(Collectors.toSet());
        return alerts.stream()
                .filter(a -> !hidden.contains(a.getId()))
                .map(WeatherAlertService::toResponse)
                .toList();
    }

    /** «Зрозуміло» — ховає попередження лише для цього учасника. Повторний виклик нічого не робить. */
    @Transactional
    public void dismiss(UUID userId, UUID tripId, UUID alertId) {
        access.require(userId, tripId, TripRole.VIEWER);
        WeatherAlert alert = alertRepository.findByIdAndTripId(alertId, tripId)
                .orElseThrow(() -> new TripNotFoundException("Попередження не знайдено"));
        if (dismissalRepository.existsByAlertIdAndUserId(alert.getId(), userId)) {
            return;
        }
        dismissalRepository.save(WeatherAlertDismissal.builder()
                .id(UUID.randomUUID())
                .alertId(alert.getId())
                .userId(userId)
                .build());
    }

    private static void fill(WeatherAlert target, WeatherAlertEvent.Alert source, Instant generatedAt) {
        target.setDayDate(source.date());
        target.setDayIndex(source.dayIndex() == null ? 0 : source.dayIndex());
        target.setLevel(WeatherAlertLevel.parse(source.level()));
        target.setPrecipitationMm(source.precipitationMm() == null ? 0.0 : source.precipitationMm());
        target.setOutdoorPlaces(joinNames(source.outdoorPlaces()));
        target.setSwapDate(source.swapDate());
        target.setSwapDayIndex(source.swapDayIndex());
        target.setSwapPrecipitationMm(source.swapPrecipitationMm());
        target.setGeneratedAt(generatedAt);
    }

    private static String joinNames(List<String> names) {
        if (names == null) {
            return "";
        }
        return names.stream()
                .filter(Objects::nonNull)
                .map(n -> n.replace('\n', ' ').trim())
                .filter(n -> !n.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private static WeatherAlertResponse toResponse(WeatherAlert a) {
        List<String> places = a.getOutdoorPlaces() == null || a.getOutdoorPlaces().isBlank()
                ? List.of()
                : List.of(a.getOutdoorPlaces().split("\n"));
        return new WeatherAlertResponse(
                a.getId(), a.getDayDate(), a.getDayIndex(), a.getLevel(), a.getPrecipitationMm(),
                places, a.getSwapDate(), a.getSwapDayIndex(), a.getSwapPrecipitationMm(),
                a.getGeneratedAt());
    }
}
