package com.travelplanner.gateway.config;

import java.util.Optional;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Ключ ліміту — IP клієнта. Працює і для публічних, і для захищених
     * маршрутів (на публічних принципала ще немає). RequestRateLimiter
     * у application.properties підхоплює цей bean автоматично.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(addr -> addr.getAddress() != null
                                ? addr.getAddress().getHostAddress()
                                : "unknown")
                        .orElse("unknown"));
    }
}
