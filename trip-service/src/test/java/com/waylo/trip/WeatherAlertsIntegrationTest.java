package com.waylo.trip;

import com.waylo.trip.consumer.WeatherAlertEvent;
import com.waylo.trip.domain.OutboxEvent;
import com.waylo.trip.dto.CreateTripRequest;
import com.waylo.trip.dto.TripResponse;
import com.waylo.trip.dto.UpdateTripRequest;
import com.waylo.trip.repository.OutboxRepository;
import com.waylo.trip.service.TripService;
import com.waylo.trip.service.WeatherAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Погодні попередження на боці trip-service:
 *  - порада з Kafka (trip.weather.alert) стає видимою через API, маршрут не змінюється;
 *  - «сховати» особисте й переживає оновлення цифр, але не зміну самої поради;
 *  - зміни дат і видалення подорожі йдуть у trip.itinerary.snapshot через outbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WeatherAlertsIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    TripService tripService;
    @Autowired
    WeatherAlertService weatherAlertService;
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    OutboxRepository outboxRepository;

    private TripResponse createTrip(UUID owner, LocalDate start) {
        return tripService.create(owner, "owner@waylo.test", new CreateTripRequest(
                "Rainy Rome", "Rome", "IT", 41.9, 12.5, null, start, start.plusDays(2), null));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> alerts(UUID userId, UUID tripId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/trips/{id}/weather-alerts", tripId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(res.getResponse().getContentAsString(), List.class);
    }

    private WeatherAlertEvent event(UUID tripId, LocalDate day, LocalDate swap, double mm) {
        return new WeatherAlertEvent(tripId.toString(), Instant.now(), List.of(
                new WeatherAlertEvent.Alert(day, 1, "RAIN", mm, List.of("Villa Borghese", "Lido"),
                        swap, swap == null ? null : 2, swap == null ? null : 0.2)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void alertFromKafka_isVisible_andDismissIsPersonal() throws Exception {
        UUID owner = UUID.randomUUID();
        LocalDate start = LocalDate.now().plusDays(2);
        UUID tripId = createTrip(owner, start).id();

        String payload = """
                {"tripId":"%s","generatedAt":"%s","alerts":[
                  {"date":"%s","dayIndex":1,"level":"HEAVY_RAIN","precipitationMm":14.2,
                   "outdoorPlaces":["Villa Borghese","Lido"],
                   "swapDate":"%s","swapDayIndex":2,"swapPrecipitationMm":0.3}]}
                """.formatted(tripId, Instant.now(), start, start.plusDays(1));
        kafkaTemplate.send("trip.weather.alert", tripId.toString(), payload).get();

        List<Map<String, Object>> visible = List.of();
        for (int i = 0; i < 40 && visible.isEmpty(); i++) {
            visible = alerts(owner, tripId);
            if (visible.isEmpty()) {
                Thread.sleep(500);
            }
        }
        assertEquals(1, visible.size(), "порада з Kafka має зʼявитись в API");
        Map<String, Object> alert = visible.get(0);
        assertEquals("HEAVY_RAIN", alert.get("level"));
        assertEquals(start.toString(), alert.get("date"));
        assertEquals(start.plusDays(1).toString(), alert.get("swapDate"));
        assertEquals(List.of("Villa Borghese", "Lido"), alert.get("outdoorPlaces"));

        // Чужий не бачить попереджень чужої подорожі.
        mockMvc.perform(get("/api/trips/{id}/weather-alerts", tripId)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        // Сховати — і повторне «сховати» теж ок (ідемпотентно).
        String alertId = (String) alert.get("id");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/trips/{id}/weather-alerts/{alertId}/dismiss", tripId, alertId)
                            .header("X-User-Id", owner.toString()))
                    .andExpect(status().isNoContent());
        }
        assertTrue(alerts(owner, tripId).isEmpty());

        // Нові цифри, та сама порада → лишається схованою.
        weatherAlertService.apply(event(tripId, start, start.plusDays(1), 9.0));
        assertTrue(alerts(owner, tripId).isEmpty(), "оновлення цифр не має повертати сховане");

        // Інший день для обміну → це вже нова порада, її знову видно.
        weatherAlertService.apply(event(tripId, start, start.plusDays(2), 9.0));
        List<Map<String, Object>> again = alerts(owner, tripId);
        assertEquals(1, again.size());
        assertEquals(start.plusDays(2).toString(), again.get(0).get("swapDate"));

        // Порожній набір знімає попередження.
        weatherAlertService.apply(new WeatherAlertEvent(tripId.toString(), Instant.now(), List.of()));
        assertTrue(alerts(owner, tripId).isEmpty());
    }

    @Test
    void newDates_andDelete_writeItinerarySnapshots() {
        UUID owner = UUID.randomUUID();
        LocalDate start = LocalDate.now().plusDays(10);
        UUID tripId = createTrip(owner, start).id();
        assertEquals(0, snapshots(tripId).size(), "чернетка без маршруту — знімок не потрібен");

        tripService.update(owner, tripId, new UpdateTripRequest(
                "Rainy Rome", "Rome", "IT", 41.9, 12.5, null, start.plusDays(1), start.plusDays(3), null));
        List<OutboxEvent> afterUpdate = snapshots(tripId);
        assertEquals(1, afterUpdate.size());
        assertTrue(afterUpdate.get(0).getPayload().contains(start.plusDays(1).toString()));

        tripService.delete(owner, tripId);
        List<OutboxEvent> afterDelete = snapshots(tripId);
        assertEquals(2, afterDelete.size());
        assertTrue(afterDelete.stream().anyMatch(e -> e.getPayload().replace(" ", "").contains("\"deleted\":true")));
    }

    private List<OutboxEvent> snapshots(UUID tripId) {
        return outboxRepository.findAll().stream()
                .filter(e -> tripId.equals(e.getAggregateId()))
                .filter(e -> "trip.itinerary.snapshot".equals(e.getEventType()))
                .toList();
    }
}
