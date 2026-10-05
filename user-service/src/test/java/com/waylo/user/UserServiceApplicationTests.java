package com.waylo.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Смоук-тест: контекст піднімається на реальному Postgres (Testcontainers) з
 * прогнаною міграцією Flyway і всіма бінами (KeyConfig, JwtService, security).
 * HTTP-флоу (register/login/refresh) перевіряємо вручну — див. runbook.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserServiceApplicationTests {

    // Ефемерні RS256-ключі: генеруємо в тимчасові файли й вказуємо на них.
    // Без цього KeyConfig не знайде ключі й контекст не підніметься.
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

    @Test
    void contextLoads() {
    }
}
