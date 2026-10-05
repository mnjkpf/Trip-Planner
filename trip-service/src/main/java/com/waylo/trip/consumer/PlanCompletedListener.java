package com.waylo.trip.consumer;

import com.waylo.trip.service.PlanResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Споживач trip.plan.completed — замикає асинхронний потік планування.
 * Парсить подію й делегує застосування в PlanResultService (там і транзакція,
 * і перевірка ідемпотентності).
 */
@Component
public class PlanCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(PlanCompletedListener.class);

    private final JsonMapper jsonMapper;
    private final PlanResultService planResultService;

    public PlanCompletedListener(JsonMapper jsonMapper, PlanResultService planResultService) {
        this.jsonMapper = jsonMapper;
        this.planResultService = planResultService;
    }

    @KafkaListener(topics = "${app.topics.plan-completed}")
    public void onPlanCompleted(String payload) {
        PlanCompletedEvent event = jsonMapper.readValue(payload, PlanCompletedEvent.class);
        log.info("отримано trip.plan.completed: job={}, trip={}, днів={}",
                event.jobId(), event.tripId(), event.days() == null ? 0 : event.days().size());
        planResultService.apply(event);
    }
}
