package com.travelplanner.planner;

import com.travelplanner.planner.client.PlaceClient;
import com.travelplanner.planner.client.PlaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Повний Kafka loop планувальника: продюсимо trip.plan.requested → planner консюмить,
 * дістає POI з place-service (замокано) → будує маршрут → публікує trip.plan.completed,
 * яке перехоплює тестовий колектор. place-service справжнім не піднімаємо — його роль
 * грає @MockitoBean PlaceClient (тестуємо логіку планувальника, не HTTP place-service).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PlanLoopIntegrationTest.CompletedCollector.class})
class PlanLoopIntegrationTest {

    @MockitoBean
    PlaceClient placeClient;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    JsonMapper jsonMapper;

    @TestConfiguration
    static class CompletedCollector {
        static final BlockingQueue<String> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "${app.topics.plan-completed}", groupId = "test-completed-collector")
        void collect(String payload) {
            received.add(payload);
        }
    }

    @BeforeEach
    void clearQueue() {
        CompletedCollector.received.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void requested_isPlanned_andCompletedEventEmitted() throws Exception {
        // given: place-service повертає 3 POI (мок)
        Mockito.when(placeClient.searchNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        new PlaceDto("p1", "Colosseum", 41.89, 12.49),
                        new PlaceDto("p2", "Vatican", 41.90, 12.45),
                        new PlaceDto("p3", "Trevi Fountain", 41.90, 12.48)));

        String jobId = UUID.randomUUID().toString();
        String tripId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("jobId", jobId);
        requested.put("tripId", tripId);
        requested.put("userId", userId);
        requested.put("destinationName", "Rome");
        requested.put("destinationLat", 41.9);
        requested.put("destinationLon", 12.5);
        requested.put("startDate", "2026-10-01");
        requested.put("endDate", "2026-10-02");          // 2 дні включно

        kafkaTemplate.send("trip.plan.requested", tripId, jsonMapper.writeValueAsString(requested)).get();

        // then: planner публікує trip.plan.completed
        String completed = CompletedCollector.received.poll(30, TimeUnit.SECONDS);
        assertNotNull(completed, "planner мав опублікувати trip.plan.completed");

        Map<String, Object> event = jsonMapper.readValue(completed, Map.class);
        assertEquals(jobId, event.get("jobId"));
        assertEquals(tripId, event.get("tripId"));
        assertEquals(userId, event.get("userId"));
        assertEquals("COMPLETED", event.get("status"));

        List<Map<String, Object>> days = (List<Map<String, Object>>) event.get("days");
        assertEquals(2, days.size());   // 01–02 включно

        List<Map<String, Object>> day1 = (List<Map<String, Object>>) days.get(0).get("items");
        List<Map<String, Object>> day2 = (List<Map<String, Object>>) days.get(1).get("items");
        assertEquals(3, day1.size() + day2.size());   // усі 3 POI розкидані по днях
        assertEquals("p1", day1.get(0).get("placeId"));
    }
}
