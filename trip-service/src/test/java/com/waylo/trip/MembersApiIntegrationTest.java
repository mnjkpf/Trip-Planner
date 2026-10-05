package com.waylo.trip;

import com.waylo.trip.client.UserClient;
import com.waylo.trip.client.UserLookup;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Спільні подорожі. Головне, що перевіряється — розведення кодів: не учасник
 * отримує 404 (подорожі для нього не існує), а учасник зі слабкою роллю — 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MembersApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @MockitoBean
    UserClient userClient;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(String json) {
        return jsonMapper.readValue(json, List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private String createTrip(UUID owner) throws Exception {
        String body = """
                {"title":"Dublin trip","destinationName":"Dublin","destinationCountry":"IE",
                 "destinationLat":53.35,"destinationLon":-6.26,
                 "startDate":"2026-10-19","endDate":"2026-10-23"}
                """;
        MvcResult res = mockMvc.perform(post("/api/trips")
                        .header("X-User-Id", owner.toString())
                        .header("X-User-Email", "owner@waylo.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Map<String, Object> created = asMap(res.getResponse().getContentAsString());
        assertEquals("OWNER", created.get("role"), "автор одразу власник");
        return (String) created.get("id");
    }

    private String invite(UUID owner, String tripId, UUID invitedId, String email, String role) throws Exception {
        Mockito.when(userClient.findByEmail(email))
                .thenReturn(Optional.of(new UserLookup(invitedId, email, "Guest")));
        MvcResult res = mockMvc.perform(post("/api/trips/" + tripId + "/members")
                        .header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        List<Map<String, Object>> members = asList(res.getResponse().getContentAsString());
        assertEquals("OWNER", members.get(0).get("role"), "власник першим у списку");
        // Шукаємо саме запрошеного за поштою, а не за позицією: у подорожі вже
        // можуть бути інші учасники, і друге запрошення поверне довший список.
        return (String) members.stream()
                .filter(m -> email.equals(m.get("email")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("запрошеного немає у списку: " + email))
                .get("id");
    }

    @Test
    void editorSeesAndEditsTrip_viewerOnlyReads() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        String tripId = createTrip(owner);

        invite(owner, tripId, editor, "editor@waylo.test", "EDITOR");
        invite(owner, tripId, viewer, "viewer@waylo.test", "VIEWER");

        // подорож зʼявилась у списку обох
        for (UUID u : List.of(editor, viewer)) {
            MvcResult res = mockMvc.perform(get("/api/trips").header("X-User-Id", u.toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals(1, asList(res.getResponse().getContentAsString()).size());
        }

        String budget = "{\"category\":\"FOOD\",\"title\":\"Lunch\",\"amount\":20.00,\"currency\":\"EUR\"}";

        mockMvc.perform(post("/api/trips/" + tripId + "/expenses")
                        .header("X-User-Id", editor.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(budget))
                .andExpect(status().isOk());

        // глядач читає…
        mockMvc.perform(get("/api/trips/" + tripId + "/budget").header("X-User-Id", viewer.toString()))
                .andExpect(status().isOk());
        // …але не пише
        mockMvc.perform(post("/api/trips/" + tripId + "/expenses")
                        .header("X-User-Id", viewer.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(budget))
                .andExpect(status().isForbidden());
    }

    @Test
    void strangerGets404_whileViewerGets403() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String tripId = createTrip(owner);
        invite(owner, tripId, viewer, "viewer@waylo.test", "VIEWER");

        // чужому подорожі не існує
        mockMvc.perform(get("/api/trips/" + tripId).header("X-User-Id", stranger.toString()))
                .andExpect(status().isNotFound());
        // глядач її бачить, але видалити не може — і це вже 403, не 404
        mockMvc.perform(get("/api/trips/" + tripId).header("X-User-Id", viewer.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/trips/" + tripId).header("X-User-Id", viewer.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyOwnerManagesMembers_andCanChangeRole() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        String tripId = createTrip(owner);
        String memberId = invite(owner, tripId, editor, "editor@waylo.test", "EDITOR");

        // редактор не керує складом
        Mockito.when(userClient.findByEmail("third@waylo.test"))
                .thenReturn(Optional.of(new UserLookup(UUID.randomUUID(), "third@waylo.test", null)));
        mockMvc.perform(post("/api/trips/" + tripId + "/members")
                        .header("X-User-Id", editor.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"third@waylo.test\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());

        // власник понижує роль
        MvcResult res = mockMvc.perform(put("/api/trips/" + tripId + "/members/" + memberId)
                        .header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("VIEWER", asList(res.getResponse().getContentAsString()).get(1).get("role"));
    }

    @Test
    void memberCanLeaveByThemselves_butOwnerCannotBeRemoved() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        String tripId = createTrip(owner);
        String memberId = invite(owner, tripId, editor, "editor@waylo.test", "EDITOR");

        MvcResult res = mockMvc.perform(get("/api/trips/" + tripId + "/members")
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isOk())
                .andReturn();
        String ownerMemberId = (String) asList(res.getResponse().getContentAsString()).get(0).get("id");

        mockMvc.perform(delete("/api/trips/" + tripId + "/members/" + ownerMemberId)
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isForbidden());

        // учасник виходить сам
        mockMvc.perform(delete("/api/trips/" + tripId + "/members/" + memberId)
                        .header("X-User-Id", editor.toString()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/trips/" + tripId).header("X-User-Id", editor.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitingUnknownEmail_is404_andDuplicateIs409() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        String tripId = createTrip(owner);

        Mockito.when(userClient.findByEmail("nobody@waylo.test")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/trips/" + tripId + "/members")
                        .header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@waylo.test\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isNotFound());

        invite(owner, tripId, editor, "editor@waylo.test", "EDITOR");
        Mockito.when(userClient.findByEmail("editor@waylo.test"))
                .thenReturn(Optional.of(new UserLookup(editor, "editor@waylo.test", "Guest")));
        mockMvc.perform(post("/api/trips/" + tripId + "/members")
                        .header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"editor@waylo.test\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
    }
}
