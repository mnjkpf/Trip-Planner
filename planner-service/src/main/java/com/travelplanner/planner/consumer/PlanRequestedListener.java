package com.travelplanner.planner.consumer;

import com.travelplanner.planner.planning.PlanRequest;
import com.travelplanner.planner.planning.PlanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
                LocalDate.parse((String) event.get("endDate")));

        planningService.plan(req);   // дістати POI → побудувати → опублікувати (блокується на ack)

        // Позначаємо оброблений тільки після успішної публікації результату.
        redis.opsForValue().set(key, Instant.now().toString(), DEDUP_TTL);
    }

    private static double asDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String payload) {
        return jsonMapper.readValue(payload, Map.class);
    }
}
