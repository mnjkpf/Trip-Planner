package com.waylo.trip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Публічні посилання: власник створює/відкликає, гість читає БЕЗ X-User-Id.
 * Головне, що тут перевіряється — межа доступу: чужий не поділиться, відкликане
 * посилання мертве, а гостьовий DTO не тягне за собою зайвих полів подорожі.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShareApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private String createTrip(UUID userId) throws Exception {
        String body = """
                {"title":"Dublin trip","destinationName":"Dublin","destinationCountry":"IE",
                 "destinationLat":53.35,"destinationLon":-6.26,
                 "startDate":"2026-10-19","endDate":"2026-10-23"}
                """;
        MvcResult res = mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return (String) asMap(res.getResponse().getContentAsString()).get("id");
    }

    private String share(UUID userId, String tripId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/trips/" + tripId + "/share")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = asMap(res.getResponse().getContentAsString());
        String token = (String) body.get("token");
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("/s/" + token, body.get("path"));
        return token;
    }

    @Test
    void shareIsIdempotent_andGuestCanReadWithoutAuth() throws Exception {
        UUID owner = UUID.randomUUID();
        String tripId = createTrip(owner);

        String token = share(owner, tripId);
        assertEquals(token, share(owner, tripId), "повторний share має повертати те саме посилання");

        // гість — БЕЗ X-User-Id
        MvcResult res = mockMvc.perform(get("/api/public/trips/" + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> shared = asMap(res.getResponse().getContentAsString());

        assertEquals("Dublin trip", shared.get("title"));
        assertEquals("Dublin", shared.get("destinationName"));
        assertNotNull(shared.get("days"));
        // вузький DTO: нічого зайвого про подорож гостю не віддаємо
        assertFalse(shared.containsKey("id"));
        assertFalse(shared.containsKey("userId"));
        assertFalse(shared.containsKey("status"));
        assertFalse(shared.containsKey("preferences"));
    }

    @Test
    void strangerCannotShareSomeoneElsesTrip() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String tripId = createTrip(owner);

        mockMvc.perform(post("/api/trips/" + tripId + "/share")
                        .header("X-User-Id", stranger.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokedLinkStopsWorking_andNewOneGetsDifferentToken() throws Exception {
        UUID owner = UUID.randomUUID();
        String tripId = createTrip(owner);
        String token = share(owner, tripId);

        mockMvc.perform(delete("/api/trips/" + tripId + "/share")
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/public/trips/" + token))
                .andExpect(status().isNotFound());

        String fresh = share(owner, tripId);
        assertNotEquals(token, fresh, "після відкликання токен має бути новий");
        mockMvc.perform(get("/api/public/trips/" + fresh)).andExpect(status().isOk());
    }

    @Test
    void unknownToken_is404() throws Exception {
        mockMvc.perform(get("/api/public/trips/definitely-not-a-real-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void currentLink_is404_whenNeverShared() throws Exception {
        UUID owner = UUID.randomUUID();
        String tripId = createTrip(owner);

        mockMvc.perform(get("/api/trips/" + tripId + "/share")
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isNotFound());

        String token = share(owner, tripId);
        MvcResult res = mockMvc.perform(get("/api/trips/" + tripId + "/share")
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(token, asMap(res.getResponse().getContentAsString()).get("token"));
        assertTrue(((String) asMap(res.getResponse().getContentAsString()).get("path")).startsWith("/s/"));
    }
}
