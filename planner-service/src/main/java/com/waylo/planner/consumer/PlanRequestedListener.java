package com.waylo.planner.consumer;

import com.waylo.planner.planning.PlanRequest;
import com.waylo.planner.planning.PlanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Споживач trip.plan.requested — вхід у планувальник.
 *
 * Ідемпотентність (at-least-once від outbox дає можливі дублі):
 *   1) якщо job уже оброблений (є ключ у Redis) — пропускаємо;
 *   2) інакше будуємо маршрут і публікуємо результат;
 *   3) позначаємо оброблений у Redis ЛИШЕ ПІСЛЯ успіху (mark-after-success).
 * Якщо крок 2 впаде — ключ не ставиться, подія прийде знову й обробиться.
 * Повторна публікація trip.plan.completed можлива — тому споживач на боці
 * trip-service теж має бути ідемпотентним (наступний інкремент).
 *
 * Схема події еволюціонує «м'яко»: поля побажань опційні, а старі події без них
 * читаються як null і плануються на дефолтах.
 */
@Component
public class PlanRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PlanRequestedListener.class);
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final PlanningService planningService;

    public PlanRequestedListener(StringRedisTemplate redis,
                                 JsonMapper jsonMapper,
                                 PlanningService planningService) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.planningService = planningService;
    }

    @KafkaListener(topics = "${app.topics.plan-requested}")
    public void onPlanRequested(String payload) {
        Map<String, Object> event = parse(payload);
        String jobId = (String) event.get("jobId");
        if (jobId == null) {
            log.warn("plan.requested без jobId — пропускаю payload: {}", payload);
            return;
        }

        String key = "planner:processed:" + jobId;
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            log.info("plan.requested job {} уже оброблявся — дубль пропущено", jobId);
            return;
        }

        PlanRequest req = new PlanRequest(
                jobId,
                (String) event.get("tripId"),
                (String) event.get("userId"),
                asDouble(event.get("destinationLat")),
                asDouble(event.get("destinationLon")),
                LocalDate.parse((String) event.get("startDate")),
                LocalDate.parse((String) event.get("endDate")),
                asText(event.get("pace")),
                asStringList(event.get("interests")),
                asInteger(event.get("searchRadiusM")),
                asTime(event.get("dayStartTime")));

        planningService.plan(req);   // дістати POI → побудувати → опублікувати (блокується на ack)

        // Позначаємо оброблений тільки після успішної публікації результату.
        redis.opsForValue().set(key, Instant.now().toString(), DEDUP_TTL);
    }

    private static double asDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = asText(value);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            log.warn("searchRadiusM не число ({}) — беру дефолт", s);
            return null;
        }
    }

    /** interests приходить як CSV-рядок ("PARK,MUSEUM"), але приймаємо і JSON-масив. */
    private static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                String s = asText(o);
                if (s != null) {
                    out.add(s);
                }
            }
            return List.copyOf(out);
        }
        String csv = asText(value);
        if (csv == null) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static LocalTime asTime(Object value) {
        String s = asText(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalTime.parse(s);
        } catch (RuntimeException ex) {
            log.warn("dayStartTime не розпізнано ({}) — беру дефолт", s);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String payload) {
        return jsonMapper.readValue(payload, Map.class);
    }
}
