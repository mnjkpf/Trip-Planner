package com.travelplanner.planner.planning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Публікує trip.plan.completed у Kafka. Блокуємось на ack (.get()) — щоб слухач
 * позначив job обробленим у Redis ЛИШЕ після реальної відправки результату.
 * Ключ = tripId (порядок подій однієї подорожі), значення = JSON.
 */
@Component
public class PlanResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlanResultPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String completedTopic;

    public PlanResultPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               JsonMapper jsonMapper,
                               @Value("${app.topics.plan-completed}") String completedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.completedTopic = completedTopic;
    }

    public void publishCompleted(PlanCompletedEvent event) {
        try {
            String json = jsonMapper.writeValueAsString(event);
            kafkaTemplate.send(completedTopic, event.tripId(), json).get();
            log.info("planner → {} відправлено для job {} ({} днів)",
                    completedTopic, event.jobId(), event.days().size());
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "Не вдалося опублікувати trip.plan.completed для job " + event.jobId(), ex);
        }
    }
}
