package com.travelplanner.trip.outbox;

import com.travelplanner.trip.domain.OutboxEvent;
import com.travelplanner.trip.repository.OutboxRepository;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Дренаж outbox: періодично вичитує неопубліковані події й відправляє в Kafka.
 * Це друга половина outbox pattern — перша це запис події в ТУ САМУ транзакцію,
 * що й зміна агрегату (див. TripService.requestPlan).
 *
 * Гарантія at-least-once: спершу успішний send у брокер (чекаємо ack через .get()),
 * і ЛИШЕ ПОТІМ позначаємо published_at. Якщо процес впаде між send і збереженням —
 * подія відправиться ще раз при наступному проході (звідси потреба в ідемпотентному
 * консюмері на боці planner-service).
 *
 * Трейсинг: при записі події ми зберегли traceparent HTTP-запиту в headers. Тут
 * відновлюємо той контекст навколо send (newScope), тож observation-продюсер інжектить
 * traceparent з тим самим trace-id — і вся async-петля лягає в ОДИН трейс із запитом.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectProvider<Tracer> tracerProvider;
    private final JsonMapper jsonMapper;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectProvider<Tracer> tracerProvider,
                           JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tracerProvider = tracerProvider;
        this.jsonMapper = jsonMapper;
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
                sendWithTrace(event);   // блокуємось до ack брокера — тільки тоді вважаємо відправленим
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

    /** Відновлює trace-контекст із headers (якщо є) навколо send, інакше шле як є. */
    private void sendWithTrace(OutboxEvent event) throws Exception {
        String traceparent = extractTraceparent(event.getHeaders());
        Tracer tracer = tracerProvider.getIfAvailable();
        if (traceparent != null && tracer != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 3) {
                TraceContext ctx = tracer.traceContextBuilder()
                        .traceId(parts[1])
                        .spanId(parts[2])
                        .sampled(true)
                        .build();
                try (CurrentTraceContext.Scope scope = tracer.currentTraceContext().newScope(ctx)) {
                    send(event);
                    return;
                }
            }
        }
        send(event);
    }

    private void send(OutboxEvent event) throws Exception {
        // топік = тип події, ключ = id агрегату (порядок у межах подорожі), значення = JSON payload
        kafkaTemplate.send(event.getEventType(),
                        event.getAggregateId().toString(),
                        event.getPayload())
                .get();
    }

    @SuppressWarnings("unchecked")
    private String extractTraceparent(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = jsonMapper.readValue(headersJson, Map.class);
            Object tp = map.get("traceparent");
            return tp == null ? null : tp.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
