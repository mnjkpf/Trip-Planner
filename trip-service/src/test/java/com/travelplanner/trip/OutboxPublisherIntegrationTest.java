package com.travelplanner.trip;

import com.travelplanner.trip.domain.OutboxEvent;
import com.travelplanner.trip.dto.CreateTripRequest;
import com.travelplanner.trip.dto.PlanJobResponse;
import com.travelplanner.trip.dto.TripResponse;
import com.travelplanner.trip.outbox.OutboxPublisher;
import com.travelplanner.trip.repository.OutboxRepository;
import com.travelplanner.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Перевіряє ДРУГУ половину outbox pattern: OutboxPublisher реально відправляє подію
 * в Kafka (Testcontainers-брокер через @ServiceConnection) і позначає published_at
 * лише після успішного ack. Якщо send не спрацював би — published_at лишився б null,
 * а attempts зріс би, і тест би впав.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIntegrationTest {

    @Autowired
    TripService tripService;
    @Autowired
    OutboxPublisher outboxPublisher;
    @Autowired
    OutboxRepository outboxRepository;

    private OutboxEvent planRequestedEventFor(UUID tripId) {
        return outboxRepository.findAll().stream()
                .filter(e -> tripId.equals(e.getAggregateId()))
                .filter(e -> "trip.plan.requested".equals(e.getEventType()))
                .findFirst().orElseThrow();
    }

    @Test
    void publishBatch_sendsPendingEventToKafka_andMarksPublished() {
        UUID userId = UUID.randomUUID();
        TripResponse trip = tripService.create(userId, new CreateTripRequest(
                "Kafka trip", "Rome", "IT", 41.9, 12.5,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07")));
        PlanJobResponse job = tripService.requestPlan(userId, trip.id());
        assertNotNull(job.jobId());

        // Реальна відправка в брокер + позначення published_at.
        outboxPublisher.publishBatch();

        OutboxEvent event = planRequestedEventFor(trip.id());
        assertNotNull(event.getPublishedAt(),
                "після успішного send у Kafka подія має бути позначена published_at");
        assertNull(event.getLastError(), "успішна відправка не лишає lastError");
    }
}
