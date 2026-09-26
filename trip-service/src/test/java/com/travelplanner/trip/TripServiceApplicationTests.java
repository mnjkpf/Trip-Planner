package com.travelplanner.trip;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Смоук-тест: контекст піднімається на Postgres + Kafka (Testcontainers) з
 * міграцією Flyway і всіма бінами (TripService, OutboxPublisher @Scheduled).
 * HTTP-флоу (create/plan) перевіряємо вручну — див. runbook.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TripServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
