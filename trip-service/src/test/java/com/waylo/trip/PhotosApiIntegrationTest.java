package com.waylo.trip;

import com.waylo.trip.client.MediaClient;
import com.waylo.trip.client.MediaTicket;
import com.waylo.trip.client.UserClient;
import com.waylo.trip.client.UserLookup;
import com.waylo.trip.domain.OutboxEvent;
import com.waylo.trip.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Фото з боку trip-service: тікет → рядок UPLOADING → подія media.ready → READY,
 * а також межі доступу й подія на видалення байтів в outbox. Сам media-service
 * тут замокано — байти до нього їдуть повз цей сервіс.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PhotosApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    OutboxRepository outboxRepository;

    @MockitoBean
    MediaClient mediaClient;
    @MockitoBean
    UserClient userClient;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(String json) {
        return jsonMapper.readValue(json, List.class);
    }

    private String createTrip(UUID owner) throws Exception {
        String body = """
                {"title":"Photo trip","destinationName":"Rome","destinationCountry":"IT",
                 "destinationLat":41.9,"destinationLon":12.5,
                 "startDate":"2026-10-19","endDate":"2026-10-23"}
                """;
        MvcResult res = mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", owner.toString())
                        .header("X-User-Email", "owner@waylo.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return (String) asMap(res.getResponse().getContentAsString()).get("id");
    }

    private Map<String, Object> ticket(UUID user, String tripId, String body) throws Exception {
        UUID mediaId = UUID.randomUUID();
        Mockito.when(mediaClient.requestTicket(any(), any()))
                .thenReturn(new MediaTicket("tok-" + mediaId, mediaId, "/api/media/upload",
                        10_485_760L, "image/jpeg,image/png", Instant.now().plusSeconds(900)));
        MvcResult res = mockMvc.perform(post("/api/trips/" + tripId + "/photos/upload-ticket")
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return asMap(res.getResponse().getContentAsString());
    }

    @Test
    void ticketCreatesPlaceholder_andMediaReadyMakesItVisible() throws Exception {
        UUID owner = UUID.randomUUID();
        String tripId = createTrip(owner);

        Map<String, Object> ticket = ticket(owner, tripId, "{\"caption\":\"Вид з вікна\"}");
        String mediaId = (String) ticket.get("mediaId");
        assertNotNull(ticket.get("ticket"));
        assertEquals("/api/media/upload", ticket.get("uploadPath"));

        List<Map<String, Object>> photos = asList(mockMvc.perform(
                        get("/api/trips/" + tripId + "/photos").header("X-User-Id", owner.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, photos.size());
        assertEquals("UPLOADING", photos.get(0).get("status"));
        assertEquals("/api/media/" + mediaId + "/thumb", photos.get(0).get("thumbUrl"));
        assertEquals(true, photos.get(0).get("mine"));

        String ready = """
                {"mediaId":"%s","context":"trip:%s","contentType":"image/jpeg","bytes":123456,
                 "width":1600,"height":900,"thumb":true,"readyAt":"%s"}
                """.formatted(mediaId, tripId, Instant.now());
        kafkaTemplate.send("media.ready", mediaId, ready).get();

        String status = null;
        for (int i = 0; i < 40 && !"READY".equals(status); i++) {
            List<Map<String, Object>> current = asList(mockMvc.perform(
                            get("/api/trips/" + tripId + "/photos").header("X-User-Id", owner.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            status = (String) current.get(0).get("status");
            if (!"READY".equals(status)) {
                Thread.sleep(500);
            } else {
                assertEquals(1600, current.get(0).get("width"));
            }
        }
        assertEquals("READY", status, "подія media.ready мала зробити фото видимим");
    }

    @Test
    void deleteWritesMediaDeleteEvent_andStrangerSeesNothing() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String tripId = createTrip(owner);
        Map<String, Object> ticket = ticket(owner, tripId, "{}");
        String photoId = (String) ticket.get("photoId");
        String mediaId = (String) ticket.get("mediaId");

        // Чужа подорож для стороннього просто не існує.
        mockMvc.perform(get("/api/trips/" + tripId + "/photos").header("X-User-Id", stranger.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/trips/" + tripId + "/photos/upload-ticket")
                        .header("X-User-Id", stranger.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/trips/" + tripId + "/photos/" + photoId)
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isNoContent());

        List<OutboxEvent> deletes = outboxRepository.findAll().stream()
                .filter(e -> "media.delete.requested".equals(e.getEventType()))
                .filter(e -> e.getPayload().contains(mediaId))
                .toList();
        assertEquals(1, deletes.size(), "видалення фото має лишити подію для media-service");
        assertTrue(deletes.get(0).getPayload().contains("trip:" + tripId));

        assertTrue(asList(mockMvc.perform(get("/api/trips/" + tripId + "/photos")
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEmpty());
    }

    @Test
    void viewerCannotUpload_butSeesGallery() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        String tripId = createTrip(owner);
        ticket(owner, tripId, "{}");

        Mockito.when(userClient.findByEmail("viewer@waylo.test"))
                .thenReturn(Optional.of(new UserLookup(viewer, "viewer@waylo.test", "Guest")));
        mockMvc.perform(post("/api/trips/" + tripId + "/members")
                        .header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"viewer@waylo.test\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated());

        List<Map<String, Object>> photos = asList(mockMvc.perform(
                        get("/api/trips/" + tripId + "/photos").header("X-User-Id", viewer.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, photos.size());
        assertFalse((Boolean) photos.get(0).get("mine"), "чуже фото не «моє»");

        mockMvc.perform(post("/api/trips/" + tripId + "/photos/upload-ticket")
                        .header("X-User-Id", viewer.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        // І чуже фото глядач не видалить.
        String photoId = (String) photos.get(0).get("id");
        mockMvc.perform(delete("/api/trips/" + tripId + "/photos/" + photoId)
                        .header("X-User-Id", viewer.toString()))
                .andExpect(status().isForbidden());
    }
}
