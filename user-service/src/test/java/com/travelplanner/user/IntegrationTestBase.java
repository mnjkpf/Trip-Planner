package com.travelplanner.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * База для HTTP-інтеграційних тестів user-service.
 *  - реальний Postgres через Testcontainers (@ServiceConnection у TestcontainersConfiguration),
 *  - повний контекст + MockMvc (весь фільтр-ланцюг Security),
 *  - ефемерні RS256-ключі в тимчасові файли (без них KeyConfig не підніме контекст).
 * MockMvc працює в режимі MOCK (без реального порту) — швидко й достатньо для перевірки HTTP-контракту.
 * JSON парсимо рідним для застосунку Jackson 3 (JsonMapper) — без зайвих тест-залежностей.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        Path dir = Files.createTempDirectory("jwt-test-keys");
        Path priv = dir.resolve("private.pem");
        Path pub = dir.resolve("public.pem");
        Files.writeString(priv, pem("PRIVATE KEY", kp.getPrivate().getEncoded()));
        Files.writeString(pub, pem("PUBLIC KEY", kp.getPublic().getEncoded()));
        priv.toFile().deleteOnExit();
        pub.toFile().deleteOnExit();

        registry.add("app.jwt.private-key-location", () -> "file:" + priv.toAbsolutePath());
        registry.add("app.jwt.public-key-location", () -> "file:" + pub.toAbsolutePath());
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
