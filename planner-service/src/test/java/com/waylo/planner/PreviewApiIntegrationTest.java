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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Публічне прев'ю маршруту: синхронний розрахунок без Kafka, без бази і без
 * користувача. Перевіряємо, що воно справді нічого не публікує — саме це
 * відрізняє його від основної гілки планування.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PreviewApiIntegrationTest {

    @MockitoBean
    PlaceClient placeClient;
    @MockitoBean
    ContextClient contextClient;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private String body(LocalDate from, LocalDate to) {
        return """
                {"destinationLat":41.9,"destinationLon":12.5,
                 "startDate":"%s","endDate":"%s","interests":["ATTRACTION"]}
                """.formatted(from, to);
    }

    @BeforeEach
    void stubDownstream() {
        Mockito.when(placeClient.searchNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        new PlaceDto("p1", "Colosseum", "ATTRACTION", 41.89, 12.49, "https://example.test/c.jpg"),
                        new PlaceDto("p2", "Vatican", "MUSEUM", 41.90, 12.45, null),
                        new PlaceDto("p3", "Trevi Fountain", "ATTRACTION", 41.90, 12.48, null)));
        Mockito.when(contextClient.fetch(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(new DestinationContext("SUMMER", "Спекотно вдень"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_buildsRoute_withoutTokenOrPersistence() throws Exception {
        LocalDate from = LocalDate.now().plusDays(10);

        MvcResult res = mockMvc.perform(post("/api/public/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(from, from.plusDays(2))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> out = asMap(res.getResponse().getContentAsString());
        List<Map<String, Object>> days = (List<Map<String, Object>>) out.get("days");

        assertEquals(3, days.size());
        assertEquals("SUMMER", out.get("season"));
        // Контекст теж доїхав — прев'ю бачить те саме, що й збережена подорож.
        assertEquals("Спекотно вдень", out.get("climateHint"));
        // Хоч десь мають бути точки, інакше прев'ю показувало б порожнечу.
        assertTrue(days.stream().anyMatch(d -> !((List<?>) d.get("items")).isEmpty()));
        // Жодних слідів подорожі чи користувача: прев'ю ні до чого не прив'язане.
        assertFalse(out.containsKey("tripId"));
        assertFalse(out.containsKey("userId"));
        assertFalse(out.containsKey("jobId"));
    }

    @Test
    void preview_rejectsBackwardsDateRange() throws Exception {
        LocalDate from = LocalDate.now().plusDays(10);
        mockMvc.perform(post("/api/public/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(from, from.minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_rejectsOverlongRange() throws Exception {
        LocalDate from = LocalDate.now().plusDays(10);
        mockMvc.perform(post("/api/public/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(from, from.plusDays(30))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_rejectsMissingCoordinates() throws Exception {
        mockMvc.perform(post("/api/public/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2027-05-01","endDate":"2027-05-03"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
