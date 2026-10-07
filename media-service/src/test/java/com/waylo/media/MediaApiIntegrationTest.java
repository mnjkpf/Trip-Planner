package com.waylo.media;

import com.waylo.media.storage.MediaKeys;
import com.waylo.media.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Повний шлях файлу: тікет → завантаження → оригінал у MinIO → подія в Kafka →
 * воркер робить мініатюру. Плюс межі: чужий/погашений тікет і завеликий файл.
 */
@SpringBootTest(properties = "app.media.max-bytes=200000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MediaApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    ObjectStore store;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /**
     * Однотонна картинка стискається в кілограбайти, хоч би якою великою була, —
     * для перевірки ліміту потрібен справжній шум.
     */
    private static byte[] heavyJpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private Map<String, Object> ticket(String context) throws Exception {
        String body = """
                {"context":"%s","userId":"%s"}
                """.formatted(context, UUID.randomUUID());
        MvcResult res = mockMvc.perform(post("/internal/media/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return asMap(res.getResponse().getContentAsString());
    }

    @Test
    void upload_storesOriginal_thenWorkerAddsThumbnail() throws Exception {
        Map<String, Object> ticket = ticket("trip:" + UUID.randomUUID());
        String token = (String) ticket.get("ticket");
        UUID mediaId = UUID.fromString((String) ticket.get("mediaId"));
        assertEquals("/api/media/upload", ticket.get("uploadPath"));

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg(1200, 800));
        mockMvc.perform(multipart("/api/media/upload").file(file).param("ticket", token))
                .andExpect(status().isAccepted());

        // Оригінал доступний одразу.
        MvcResult original = mockMvc.perform(get("/api/media/{id}", mediaId))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("image/jpeg", original.getResponse().getContentType());
        assertTrue(original.getResponse().getHeader("Cache-Control").contains("immutable"));

        // Мініатюра зʼявляється після обробки події (до 30 с).
        byte[] thumb = null;
        for (int i = 0; i < 60 && thumb == null; i++) {
            if (store.get(MediaKeys.thumb(mediaId)).isPresent()) {
                thumb = store.readAll(MediaKeys.thumb(mediaId));
            } else {
                Thread.sleep(500);
            }
        }
        assertNotNull(thumb, "воркер мав зробити мініатюру");
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(thumb));
        assertEquals(640, image.getWidth());

        MvcResult served = mockMvc.perform(get("/api/media/{id}/thumb", mediaId))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("image/jpeg", served.getResponse().getContentType());

        // Тікет одноразовий.
        mockMvc.perform(multipart("/api/media/upload").file(file).param("ticket", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownTicket_isRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg(10, 10));

        mockMvc.perform(multipart("/api/media/upload").file(file).param("ticket", "не-існує"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tooLargeOrWrongType_areRejected() throws Exception {
        String token = (String) ticket("trip:" + UUID.randomUUID()).get("ticket");
        byte[] heavy = heavyJpeg(900, 900);
        assertTrue(heavy.length > 200_000, "шумна картинка має перевищити ліміт тесту");
        MockMultipartFile big = new MockMultipartFile("file", "big.jpg", "image/jpeg", heavy);
        mockMvc.perform(multipart("/api/media/upload").file(big).param("ticket", token))
                .andExpect(status().isPayloadTooLarge());

        MockMultipartFile text = new MockMultipartFile("file", "notes.txt", "text/plain",
                "не фото".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/media/upload").file(text).param("ticket", token))
                .andExpect(status().isUnsupportedMediaType());

        // Невдалі спроби тікет не палять — користувач може перевибрати файл.
        MockMultipartFile ok = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg(400, 300));
        mockMvc.perform(multipart("/api/media/upload").file(ok).param("ticket", token))
                .andExpect(status().isAccepted());
    }

    @Test
    void missingMedia_is404() throws Exception {
        mockMvc.perform(get("/api/media/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
