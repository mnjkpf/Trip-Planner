package com.waylo.context.watch;

import com.waylo.context.advice.PlannedDay;
import com.waylo.context.advice.WeatherAdvice;
import com.waylo.context.advice.WeatherAdvisor;
import com.waylo.context.weather.DailyWeather;
import com.waylo.context.weather.ForecastWindow;
import com.waylo.context.weather.WeatherClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Погодний нагляд: для подорожей, що починаються найближчими днями, порівнює
 * прогноз із маршрутом і публікує поради в Kafka (trip.weather.alert).
 *
 * Тільки ПОРАДА — маршрут тут не змінюється й не може змінитись: context-service
 * взагалі не має доступу на запис до маршрутів. Рішення лишається за людиною.
 *
 * Подія йде лише тоді, коли порада змінилась (відбиток у watched_trips), тож
 * job можна запускати як завгодно часто — споживач не потоне в дублікатах.
 * Порожній список порад — теж подія: так trip-service знімає попередження,
 * коли прогноз покращився або маршрут переставили.
 */
@Service
public class WeatherWatchService {

    private static final Logger log = LoggerFactory.getLogger(WeatherWatchService.class);

    private final WatchedTripRepository repository;
    private final TripProjection projection;
    private final WeatherClient weatherClient;
    private final WeatherAdvisor advisor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;
    private final int lookaheadDays;
    private final Duration cacheTtl;

    /** Короткий кеш прогнозу: кілька правок маршруту поспіль не мають бити Open-Meteo щоразу. */
    private final Map<String, CachedForecast> forecastCache = new ConcurrentHashMap<>();

    private record CachedForecast(List<DailyWeather> days, Instant expiresAt) {}

    public WeatherWatchService(WatchedTripRepository repository,
                               TripProjection projection,
                               WeatherClient weatherClient,
                               WeatherAdvisor advisor,
                               KafkaTemplate<String, String> kafkaTemplate,
                               JsonMapper jsonMapper,
                               @Value("${app.topics.weather-alert}") String topic,
                               @Value("${app.weather-watch.lookahead-days:7}") int lookaheadDays,
                               @Value("${app.weather-watch.forecast-cache-minutes:30}") long cacheMinutes) {
        this.repository = repository;
        this.projection = projection;
        this.weatherClient = weatherClient;
        this.advisor = advisor;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
        this.lookaheadDays = lookaheadDays;
        this.cacheTtl = Duration.ofMinutes(Math.max(0, cacheMinutes));
    }

    /** Плановий прохід по всіх подорожах у вікні. @return скільки нових порад відправлено. */
    public int checkAll(LocalDate today) {
        int removed = repository.deleteFinishedBefore(today.minusDays(1));
        List<WatchedTrip> due = repository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
                today.plusDays(lookaheadDays), today);
        int published = 0;
        for (WatchedTrip trip : due) {
            try {
                if (check(trip, today)) {
                    published++;
                }
            } catch (RuntimeException ex) {
                // одна невдала подорож не має зупиняти решту — наступний прохід повторить
                log.warn("погодний нагляд: подорож {} не перевірено: {}", trip.getTripId(), ex.getMessage());
            }
        }
        log.info("погодний нагляд: у вікні {} подорожей, нових порад {}, завершених прибрано {}",
                due.size(), published, removed);
        return published;
    }

    /** Перевірка однієї подорожі — одразу після нового знімка маршруту. */
    public boolean checkTrip(UUID tripId, LocalDate today) {
        return repository.findById(tripId)
                .map(trip -> check(trip, today))
                .orElse(false);
    }

    /** @return true, якщо в Kafka пішла нова версія порад. */
    private boolean check(WatchedTrip trip, LocalDate today) {
        List<WeatherAdvice> advice = adviceFor(trip, today);
        if (advice == null) {
            return false;   // прогнозу не дізнались — попередні поради не чіпаємо
        }
        String fingerprint = WeatherAdvisor.fingerprint(advice);
        String last = trip.getLastFingerprint() == null ? "" : trip.getLastFingerprint();
        boolean changed = !fingerprint.equals(last);
        Instant now = Instant.now();
        if (changed) {
            publish(trip.getTripId(), advice, now);
        }
        repository.markChecked(trip.getTripId(), fingerprint, now);
        return changed;
    }

    /**
     * Поради для подорожі на сьогодні.
     * Порожній список — порадити нічого (поза вікном, маршрут порожній, погода гарна);
     * null — прогноз недоступний, і про погоду ми просто не знаємо.
     */
    private List<WeatherAdvice> adviceFor(WatchedTrip trip, LocalDate today) {
        if (!isDue(trip, today)) {
            return List.of();
        }
        List<PlannedDay> days = projection.days(trip).stream()
                .filter(d -> !d.date().isBefore(today))   // минулі дні вже не переставиш
                .toList();
        if (days.isEmpty()) {
            return List.of();
        }
        ForecastWindow window = ForecastWindow.of(trip.getStartDate(), trip.getEndDate(), today);
        if (window.isEmpty()) {
            return List.of();
        }
        List<DailyWeather> forecast = forecast(trip.getLat(), trip.getLon(), window.from(), window.to());
        if (forecast.isEmpty()) {
            return null;
        }
        return advisor.advise(days, forecast);
    }

    /** У вікні нагляду: ще не закінчилась і стартує не пізніше ніж за lookaheadDays. */
    boolean isDue(WatchedTrip trip, LocalDate today) {
        return !trip.getEndDate().isBefore(today)
                && !trip.getStartDate().isAfter(today.plusDays(lookaheadDays));
    }

    private List<DailyWeather> forecast(double lat, double lon, LocalDate from, LocalDate to) {
        String key = String.format(Locale.ROOT, "%.3f:%.3f:%s:%s", lat, lon, from, to);
        Instant now = Instant.now();
        CachedForecast hit = forecastCache.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) {
            return hit.days();
        }
        List<DailyWeather> days = weatherClient.forecast(lat, lon, from, to);
        if (!days.isEmpty() && !cacheTtl.isZero()) {
            if (forecastCache.size() > 500) {
                forecastCache.values().removeIf(c -> !c.expiresAt().isAfter(now));
            }
            forecastCache.put(key, new CachedForecast(days, now.plus(cacheTtl)));
        }
        return days;
    }

    /** Синхронно чекаємо ack брокера: відбиток зберігаємо лише після успішної відправки. */
    private void publish(UUID tripId, List<WeatherAdvice> advice, Instant generatedAt) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (WeatherAdvice a : advice) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", a.date().toString());
            m.put("dayIndex", a.dayIndex());
            m.put("level", a.level().name());
            m.put("precipitationMm", a.precipitationMm());
            m.put("outdoorPlaces", a.outdoorPlaces());
            m.put("swapDate", a.swapDate() == null ? null : a.swapDate().toString());
            m.put("swapDayIndex", a.swapDayIndex());
            m.put("swapPrecipitationMm", a.swapPrecipitationMm());
            alerts.add(m);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", tripId.toString());
        payload.put("generatedAt", generatedAt.toString());
        payload.put("alerts", alerts);
        String json = jsonMapper.writeValueAsString(payload);
        try {
            kafkaTemplate.send(topic, tripId.toString(), json).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("перервано під час відправки поради", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("не вдалося відправити пораду в Kafka", e);
        }
        log.info("погодна порада для подорожі {}: {} попереджень", tripId, advice.size());
    }
}
