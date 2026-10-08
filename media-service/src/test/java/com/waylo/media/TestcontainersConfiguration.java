package com.waylo.media;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
    }

    // Готового @ServiceConnection для S3 немає — адресу віддаємо реєстратором властивостей.
    // Без -s3.config SeaweedFS працює в режимі «дозволено все», тож ключі тут будь-які:
    // тест перевіряє нашу логіку, а не чужу автентифікацію.
    @Bean
    GenericContainer<?> objectsContainer() {
        return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:latest"))
                .withCommand("server", "-dir=/data", "-master.volumeSizeLimitMB=256", "-s3", "-s3.port=9000")
                .withExposedPorts(9000)
                .waitingFor(Wait.forListeningPort());
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(@Qualifier("objectsContainer") GenericContainer<?> objects) {
        return registry -> {
            registry.add("storage.s3.endpoint", () -> "http://" + objects.getHost() + ":" + objects.getMappedPort(9000));
            registry.add("storage.s3.access-key", () -> "waylo");
            registry.add("storage.s3.secret-key", () -> "waylo-local-secret");
        };
    }
}
