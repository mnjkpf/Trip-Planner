package com.waylo.trip;

import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.PlanJob;
import com.waylo.trip.domain.PlanJobStatus;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.domain.TripStatus;
import com.waylo.trip.dto.CreateTripRequest;
import com.waylo.trip.dto.PlanJobResponse;
import com.waylo.trip.dto.TripResponse;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.PlanJobRepository;
import com.waylo.trip.repository.TripDayRepository;
import com.waylo.trip.repository.TripRepository;
import com.waylo.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Замикає асинхронний потік на боці trip-service: продюсимо trip.plan.completed →
 * слухач пише маршрут (TripDay/ItineraryItem), job → COMPLETED, trip → PLANNED.
 * Разом з планувальником це дає повний ланцюг:
 *   requestPlan → outbox → Kafka → planner → trip.plan.completed → Kafka → тут.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlanCompletedListenerIntegrationTest {

    @Autowired
    TripService tripService;
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    PlanJobRepository planJobRepository;
    @Autowired
    TripDayRepository tripDayRepository;
    @Autowired
    ItineraryItemRepository itineraryItemRepository;
    @Autowired
    TripRepository tripRepository;

    @Test
    void completedEvent_writesItinerary_marksJobCompleted_andTripPlanned() throws Exception {
        UUID userId = UUID.randomUUID();
        TripResponse trip = tripService.create(userId, "owner@waylo.test", new CreateTripRequest(
                "Rome trip", "Rome", "IT", 41.9, 12.5, null,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), null));
        PlanJobResponse job = tripService.requestPlan(userId, trip.id());

        String placeA = UUID.randomUUID().toString();
        String placeB = UUID.randomUUID().toString();
        String payload = """
                {"jobId":"%s","tripId":"%s","userId":"%s","status":"COMPLETED",
                 "days":[
                   {"dayNumber":1,"date":"2026-10-01","items":[
                     {"placeId":"%s","name":"Colosseum","lat":41.89,"lon":12.49,"order":1}]},
                   {"dayNumber":2,"date":"2026-10-02","items":[
                     {"placeId":"%s","name":"Vatican","lat":41.90,"lon":12.45,"order":1}]}
                 ]}
                """.formatted(job.jobId(), trip.id(), userId, placeA, placeB);

        kafkaTemplate.send("trip.plan.completed", trip.id().toString(), payload).get();

        // чекаємо, поки слухач застосує результат (до 20с)
        UUID jobId = job.jobId();
        PlanJob applied = null;
        for (int i = 0; i < 40 && applied == null; i++) {
            PlanJob current = planJobRepository.findById(jobId).orElseThrow();
            if (current.getStatus() == PlanJobStatus.COMPLETED) {
                applied = current;
            } else {
                Thread.sleep(500);
            }
        }
        assertNotNull(applied, "job мав стати COMPLETED після trip.plan.completed");
        assertNotNull(applied.getCompletedAt());

        UUID tripUuid = trip.id();
        List<TripDay> days = tripDayRepository.findByTripIdOrderByDayIndexAsc(tripUuid);
        assertEquals(2, days.size());
        assertEquals(1, days.get(0).getDayIndex());
        assertEquals(LocalDate.parse("2026-10-01"), days.get(0).getDayDate());

        List<ItineraryItem> day1 = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(days.get(0).getId());
        assertEquals(1, day1.size());
        assertEquals("Colosseum", day1.get(0).getPlaceName());
        assertEquals(1, day1.get(0).getOrderIndex());

        Trip reloaded = tripRepository.findById(tripUuid).orElseThrow();
        assertEquals(TripStatus.PLANNED, reloaded.getStatus());
    }
}
