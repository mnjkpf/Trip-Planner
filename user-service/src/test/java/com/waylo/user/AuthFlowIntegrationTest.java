package com.waylo.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-контракт user-service через реальний DispatcherServlet (MockMvc) і реальний Postgres.
 * Перевіряємо повний auth-флоу та ключові помилки — те, на що спирається gateway і решта системи.
 */
class AuthFlowIntegrationTest extends IntegrationTestBase {

    private String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"secret12345","displayName":"Roman"}
                """.formatted(email);
    }

    // sub у access-токені = user.id (те, що gateway кладе в X-User-Id)
    private String subjectOf(String jwt) {
        String[] parts = jwt.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return (String) asMap(payload).get("sub");
    }

    @Test
    void register_login_refresh_happyPath() throws Exception {
        String email = uniqueEmail();

        MvcResult reg = mockMvc.perform(post("/api/user/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<String, Object> regResp = asMap(reg.getResponse().getContentAsString());
        assertNotNull(regResp.get("accessToken"));
        assertNotNull(regResp.get("refreshToken"));
        assertEquals("Bearer", regResp.get("tokenType"));

        String loginBody = """
                {"email":"%s","password":"secret12345"}
                """.formatted(email);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = (String) asMap(login.getResponse().getContentAsString()).get("refreshToken");
        assertNotNull(refreshToken);

        String refreshBody = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> refreshResp = asMap(refreshed.getResponse().getContentAsString());
        assertNotNull(refreshResp.get("accessToken"));
        // ротація refresh-токена: новий має відрізнятись від використаного
        assertNotEquals(refreshToken, refreshResp.get("refreshToken"));
    }

    @Test
    void me_returnsProfile_forUserIdFromToken() throws Exception {
        String email = uniqueEmail();
        MvcResult reg = mockMvc.perform(post("/api/user/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = (String) asMap(reg.getResponse().getContentAsString()).get("accessToken");
        String userId = subjectOf(accessToken);

        MvcResult me = mockMvc.perform(get("/api/user/me")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> profile = asMap(me.getResponse().getContentAsString());
        assertEquals(userId, profile.get("id"));
        assertEquals(email, profile.get("email"));
        assertEquals("USER", profile.get("role"));
    }

    @Test
    void me_withoutHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/user/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/user/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        String body = """
                {"email":"not-an-email","password":"secret12345","displayName":"Roman"}
                """;
        mockMvc.perform(post("/api/user/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/user/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());
        String badLogin = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings("unchecked")
    void jwks_returnsOnlyPublicRsaKey() throws Exception {
        MvcResult res = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = asMap(res.getResponse().getContentAsString());
        assertTrue(body.containsKey("keys"));
        List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");
        assertFalse(keys.isEmpty());
        Map<String, Object> first = keys.get(0);
        assertEquals("RSA", first.get("kty"));
        assertEquals("waylo-key", first.get("kid"));
        // приватної частини (d) у JWKS бути НЕ повинно
        assertFalse(first.containsKey("d"));
    }
}
