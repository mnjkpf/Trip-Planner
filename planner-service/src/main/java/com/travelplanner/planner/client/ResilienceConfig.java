package com.travelplanner.planner.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Спільний реєстр circuit breaker'ів (дефолтна конфігурація: sliding window 100,
 * поріг відмов 50%, відкриття на 60с). Береться Resilience4j core, а не Spring Cloud
 * CircuitBreaker — щоб виклик лишався на ТОМУ САМОМУ потоці й не рвав trace-контекст.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
}
