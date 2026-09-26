package com.travelplanner.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Ключ для rate-limiter'а: автентифікованого користувача лімітуємо за його id (sub з JWT),
 * анонімні запити (логін/реєстрація) — за IP. Ключ НІКОЛИ не порожній, інакше RequestRateLimiter
 * із deny-empty-key відхилив би запит.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(p -> "user:" + ((JwtAuthenticationToken) p).getToken().getSubject())
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var addr = exchange.getRequest().getRemoteAddress();
                    return "ip:" + (addr != null ? addr.getAddress().getHostAddress() : "unknown");
                }));
    }
}
