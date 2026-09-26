package com.travelplanner.planner.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Спільний реєстр circuit breaker'ів (дефолт: sliding window 100, поріг 50%,
 * open на 60с). Береться Resilience4j core, а не Spring Cloud CircuitBreaker —
 * щоб виклик лишався на ТОМУ САМОМУ потоці й не рвав trace-контекст.
 *
 * Метрики CB (resilience4j_circuitbreaker_*) прив'язуємо до Micrometer -> вони
 * йдуть і в /actuator/prometheus, і по OTLP у LGTM (Grafana).
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    public MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry registry) {
        // Spring Boot автоматично прив'яже цей MeterBinder до MeterRegistry.
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
    }
}
