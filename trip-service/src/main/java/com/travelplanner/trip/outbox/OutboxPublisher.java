package com.travelplanner.trip.outbox;

import com.travelplanner.trip.domain.OutboxEvent;
import com.travelplanner.trip.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Дренаж outbox: періодично вичитує неопубліковані події й відправляє в Kafka.
 * Це друга половина outbox pattern — перша це запис події в ТУ САМУ транзакцію,
 * що й зміна агрегату (див. TripService.requestPlan).
 *
 * Гарантія at-least-once: спершу успішний send у брокер (чекаємо ack через .get()),
 * і ЛИШЕ ПОТІМ позначаємо published_at. Якщо процес впаде між send і збереженням —
 * подія відправиться ще раз при наступному проході (звідси потреба в ідемпотентному
 * консюмері на боці planner-service).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            try {
                // топік = тип події (напр. trip.plan.requested),
                // ключ = id агрегату (усі події однієї подорожі йдуть в один партишн → порядок),
                // значення = JSON payload.
                kafkaTemplate.send(event.getEventType(),
                                event.getAggregateId().toString(),
                                event.getPayload())
                        .get();   // блокуємось до ack брокера — тільки тоді вважаємо відправленим

                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                log.info("outbox → подію {} відправлено в топік '{}'", event.getId(), event.getEventType());
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(ex.getMessage());
                outboxRepository.save(event);
                log.warn("outbox: не вдалося відправити подію {} (спроба {}): {}",
                        event.getId(), event.getAttempts(), ex.getMessage());
            }
        }
    }
}
