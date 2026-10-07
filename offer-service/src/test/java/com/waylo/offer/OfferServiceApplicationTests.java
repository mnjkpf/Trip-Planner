package com.waylo.offer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Smoke-test: контекст стартує навіть коли SERPAPI_API_KEY порожній. */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.cache.type=none",
    "management.health.redis.enabled=false",
})
class OfferServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}
