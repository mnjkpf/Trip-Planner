package com.waylo.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Контекст піднімається: MinIO-клієнт, бакет, Redis-тікети й Kafka-лісенери. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MediaServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
