package com.travelplanner.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Єдине джерело заголовків ідентичності для downstream-сервісів.
 *
 * Виконує дві речі на КОЖНОМУ запиті, ще до маршрутизації:
 *  1. Зрізає будь-які X-User-* заголовки, надіслані клієнтом — інакше їх
 *     можна підробити (X-User-Role: ADMIN) в обхід автентифікації.
 *  2. Якщо запит автентифікований, проставляє довірені X-User-Id / X-User-Role
 *     з валідованого JWT.
 *
 * Downstream-сервіси довіряють цим заголовкам, тому вони мають бути
 * недосяжні напряму — тільки через gateway.
 */
@Component
public class IdentityHeadersGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    private static final String DEFAULT_ROLE = "USER";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(Jwt.class::isInstance)
                .cast(Jwt.class)
                .map(jwt -> withTrustedIdentity(exchange, jwt))
                // немає токена (публічний/анонімний запит) → просто зрізаємо
                .defaultIfEmpty(withoutIdentity(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withTrustedIdentity(ServerWebExchange exchange, Jwt jwt) {
        // X-User-Id несе стабільний UUID (claim "uid"); фолбек на subject —
        // на випадок старих токенів. Downstream парсить це як UUID.
        String uid = jwt.getClaimAsString("uid");
        String userId = (uid != null && !uid.isBlank()) ? uid : jwt.getSubject();
        String role = jwt.getClaimAsString("role");
        String roleFinal = (role != null && !role.isBlank()) ? role : DEFAULT_ROLE;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);          // спершу прибрати клієнтські
                    h.remove(USER_ROLE);
                    h.set(USER_ID, userId);     // потім проставити довірені
                    h.set(USER_ROLE, roleFinal);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withoutIdentity(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLE);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        // Найвищий пріоритет: зрізання має статися до будь-якої маршрутизації.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
