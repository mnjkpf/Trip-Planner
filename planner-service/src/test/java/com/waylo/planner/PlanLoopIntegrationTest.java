package com.waylo.planner;

import com.waylo.planner.client.ContextClient;
import com.waylo.planner.client.DestinationContext;
import com.waylo.planner.client.PlaceClient;
import com.waylo.planner.client.PlaceDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Повний Kafka loop планувальника: продюсимо trip.plan.requested → planner консюмить,
 * дістає POI (place-service замокано) + контекст (context-service замокано) → будує
 * маршрут → публікує trip.plan.completed (із сезоном), яке перехоплює тестовий колектор.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PlanLoopIntegrationTest.CompletedCollector.class})
class PlanLoopIntegrationTest {

    @MockitoBean
    PlaceClient placeClient;
    @MockitoBean
    ContextClient contextClient;

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
    void requested_isPlanned_andCompletedEventEmitted_withContext() throws Exception {
        Mockito.when(placeClient.searchNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        new PlaceDto("p1", "Colosseum", "ATTRACTION", 41.89, 12.49),
                        new PlaceDto("p2", "Vatican", "MUSEUM", 41.90, 12.45),
                        new PlaceDto("p3", "Trevi Fountain", "ATTRACTION", 41.90, 12.48)));
        Mockito.when(contextClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(new DestinationContext("SUMMER", "Спекотно вдень — бери воду"));

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
        requested.put("startDate", "2026-07-01");
        requested.put("endDate", "2026-07-02");          // 2 дні включно, літо

        kafkaTemplate.send("trip.plan.requested", tripId, jsonMapper.writeValueAsString(requested)).get();

        String completed = CompletedCollector.received.poll(30, TimeUnit.SECONDS);
        assertNotNull(completed, "planner мав опублікувати trip.plan.completed");

        Map<String, Object> event = jsonMapper.readValue(completed, Map.class);
        assertEquals(jobId, event.get("jobId"));
        assertEquals("COMPLETED", event.get("status"));
        assertEquals("SUMMER", event.get("season"));                 // контекст від context-service
        assertNotNull(event.get("climateHint"));

        List<Map<String, Object>> days = (List<Map<String, Object>>) event.get("days");
        assertEquals(2, days.size());
        List<Map<String, Object>> day1 = (List<Map<String, Object>>) days.get(0).get("items");
        List<Map<String, Object>> day2 = (List<Map<String, Object>>) days.get(1).get("items");
        assertEquals(3, day1.size() + day2.size());
    }
}
