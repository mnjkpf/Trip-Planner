package com.waylo.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Проброс ідентичності з валідованого JWT у downstream-сервіси.
 *
 * Gateway перевіряє токен, а сервіси нижче довіряють заголовкам:
 *   uid  -> X-User-Id     (trip/user/wishlist читають саме його)
 *   role -> X-User-Role
 *
 * Вхідні X-User-* завжди прибираємо: клієнт не має права їх підставляти,
 * джерело істини — тільки підписаний токен.
 */
@Component
public class IdentityRelayFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> withIdentity(exchange, auth.getToken()))
                .defaultIfEmpty(stripIdentity(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt token) {
        String uid = token.getClaimAsString("uid");
        final String userId = (uid != null) ? uid : token.getSubject();
        final String role = token.getClaimAsString("role");
        return exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(USER_ID_HEADER);
                    h.remove(USER_ROLE_HEADER);
                    if (userId != null) {
                        h.set(USER_ID_HEADER, userId);
                    }
                    if (role != null) {
                        h.set(USER_ROLE_HEADER, role);
                    }
                }))
                .build();
    }

    private ServerWebExchange stripIdentity(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(USER_ID_HEADER);
                    h.remove(USER_ROLE_HEADER);
                }))
                .build();
    }

    @Override
    public int getOrder() {
        // Раніше за маршрутизацію (NettyRoutingFilter), щоб заголовки
        // потрапили у запит, який реально йде в downstream.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
