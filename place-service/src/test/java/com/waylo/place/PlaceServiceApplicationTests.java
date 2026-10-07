package com.waylo.place;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Смоук-тест: контекст піднімається на PostGIS (Testcontainers) з міграцією
 * Flyway і всіма бінами (провайдер-заглушка, GeometryFactory). HTTP-пошук
 * перевіряємо вручну — див. runbook.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlaceServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
