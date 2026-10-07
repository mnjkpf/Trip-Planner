package com.waylo.gateway;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Стартуємо справжній Netty-сервер на випадковому порті.
 * Downstream-сервіси НЕ підняті — тому перевіряємо те, що не залежить
 * від їх доступності: поведінку security та те, що роути забіндились.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @Value("${local.server.port}")
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void protectedRouteRequiresAuth() {
        // /api/trips/** — захищений; без токена security віддає 401 ЩЕ ДО роутингу.
        client().get().uri("/api/trips/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicRouteIsBoundAndOpen() {
        // /api/auth/** — permitAll і має роут. Отже запит НЕ 401 (пройшов security)
        // і НЕ 404 (роут забіндився). Downstream лежить → буде 5xx або 405 — обидва ok.
        // Якби префікс routes був неправильний — було б 404 і тест впав би.
        client().get().uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status -> {
                    assertNotEquals(401, status);
                    assertNotEquals(404, status);
                });
    }
}
