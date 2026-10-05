package com.waylo.trip;

import com.waylo.trip.domain.OutboxEvent;
import com.waylo.trip.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-контракт trip-service через реальний DispatcherServlet (MockMvc) і реальний Postgres.
 * Перевіряємо власність за X-User-Id (заголовок ставить gateway), асинхронний requestPlan (202)
 * і ГОЛОВНЕ — що подія пишеться в outbox у ТУ САМУ транзакцію, що й PlanJob (суть outbox pattern).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TripApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    OutboxRepository outboxRepository;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(String json) {
        return jsonMapper.readValue(json, List.class);
    }

    private String tripBody(String title) {
        return """
                {"title":"%s","destinationName":"Rome","destinationCountry":"IT",
                 "destinationLat":41.9,"destinationLon":12.5,
                 "startDate":"2026-10-01","endDate":"2026-10-07"}
                """.formatted(title);
    }

    // Створити подорож і повернути її id (як власник userId)
    private String createTrip(UUID userId, String title) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripBody(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return (String) asMap(res.getResponse().getContentAsString()).get("id");
    }

    @Test
    void create_list_get_happyPath() throws Exception {
        UUID userId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripBody("Rome getaway")))
                .andExpect(status().isCreated())
                .andReturn();
        Map<String, Object> trip = asMap(created.getResponse().getContentAsString());
        String tripId = (String) trip.get("id");
        assertNotNull(tripId);
        assertEquals(userId.toString(), trip.get("userId"));
        assertEquals("Rome getaway", trip.get("title"));
        assertEquals("DRAFT", trip.get("status"));

        // list — має містити нашу подорож
        MvcResult listed = mockMvc.perform(get("/api/trips")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> trips = asList(listed.getResponse().getContentAsString());
        assertTrue(trips.stream().anyMatch(t -> tripId.equals(t.get("id"))));

        // get by id
        MvcResult one = mockMvc.perform(get("/api/trips/{id}", tripId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(tripId, asMap(one.getResponse().getContentAsString()).get("id"));
    }

    @Test
    void create_missingUserId_returns401() throws Exception {
        mockMvc.perform(post("/api/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripBody("No owner")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_otherUsersTrip_returns404() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String tripId = createTrip(owner, "Owner trip");

        // чужий користувач не бачить подорож — findByIdAndUserId → порожньо → 404
        mockMvc.perform(get("/api/trips/{id}", tripId)
                        .header("X-User-Id", stranger.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String blankTitle = """
                {"title":"","destinationName":"Rome","destinationCountry":"IT",
                 "destinationLat":41.9,"destinationLon":12.5,
                 "startDate":"2026-10-01","endDate":"2026-10-07"}
                """;
        mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankTitle))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_removesTrip() throws Exception {
        UUID userId = UUID.randomUUID();
        String tripId = createTrip(userId, "To be deleted");

        mockMvc.perform(delete("/api/trips/{id}", tripId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trips/{id}", tripId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestPlan_returns202_writesOutbox_andMovesTripToPlanning() throws Exception {
        UUID userId = UUID.randomUUID();
        String tripId = createTrip(userId, "Plan me");

        MvcResult planned = mockMvc.perform(post("/api/trips/{id}/plan", tripId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isAccepted())
                .andReturn();
        Map<String, Object> job = asMap(planned.getResponse().getContentAsString());
        assertNotNull(job.get("jobId"));
        assertEquals(tripId, job.get("tripId"));
        assertEquals("PENDING", job.get("status"));

        // СУТЬ OUTBOX PATTERN: подія лежить у таблиці outbox (та сама транзакція, що й PlanJob).
        // Publisher лише позначає published_at, рядок не видаляється — тож перевірка без гонки.
        UUID tripUuid = UUID.fromString(tripId);
        List<OutboxEvent> events = outboxRepository.findAll().stream()
                .filter(e -> tripUuid.equals(e.getAggregateId()))
                .filter(e -> "trip.plan.requested".equals(e.getEventType()))
                .toList();
        assertEquals(1, events.size(), "має бути рівно одна подія trip.plan.requested для цієї подорожі");
        OutboxEvent event = events.get(0);
        assertEquals("Trip", event.getAggregateType());
        assertTrue(event.getPayload().contains(tripId), "payload має містити tripId");
        assertTrue(event.getPayload().contains(userId.toString()), "payload має містити userId");

        // trip переходить у PLANNING
        MvcResult one = mockMvc.perform(get("/api/trips/{id}", tripId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("PLANNING", asMap(one.getResponse().getContentAsString()).get("status"));
    }

    @Test
    void requestPlan_otherUser_returns404_andWritesNoOutbox() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String tripId = createTrip(owner, "Not yours to plan");

        mockMvc.perform(post("/api/trips/{id}/plan", tripId)
                        .header("X-User-Id", stranger.toString()))
                .andExpect(status().isNotFound());

        UUID tripUuid = UUID.fromString(tripId);
        boolean anyEvent = outboxRepository.findAll().stream()
                .anyMatch(e -> tripUuid.equals(e.getAggregateId()));
        assertTrue(!anyEvent, "для чужого запиту подія outbox не має зʼявитись");
    }
}
