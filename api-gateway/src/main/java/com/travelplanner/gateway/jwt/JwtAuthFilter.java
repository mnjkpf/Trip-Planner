package com.travelplanner.gateway.jwt;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilter; // payload JWT
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory; // генерує конструктор
import org.springframework.http.HttpHeaders; // Gateway filter API
import org.springframework.http.HttpStatus; // базовий клас
import org.springframework.http.server.reactive.ServerHttpRequest; // HTTP статуси та заголовки
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange; // Spring bean


import reactor.core.publisher.Mono; // reactive тип

@Component // реєструємо фільтр

public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class); // обов'язково
        this.jwtUtil = jwtUtil; // ручна інжекція
    }

    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) { // створюємо фільтр
        return (exchange, chain) -> { // exchange = request/response, chain = наступний фільтр

            String authHeader = exchange.getRequest() // беремо request
                    .getHeaders() // отримуємо headers
                    .getFirst(HttpHeaders.AUTHORIZATION); // беремо Authorization header

            if (authHeader == null || !authHeader.startsWith("Bearer ")) { // перевірка header
                return unauthorized(exchange); // якщо нема токена → 401
            }

            try {
                String token = authHeader.substring(7); // обрізаємо "Bearer "

                Claims claims = jwtUtil.validateToken(token); // валідовуємо токен

                // X-User-Id має нести стабільний UUID користувача (claim "uid"), а не email.
                // Токени, випущені user-service, містять "uid"; фолбек на subject лишаємо на випадок
                // старих токенів у обігу до їх протухання. Downstream (content-service) парсить це як UUID.
                String uid = claims.get("uid", String.class);
                String userId = (uid != null && !uid.isBlank()) ? uid : claims.getSubject();
                String role = claims.get("role", String.class); // беремо роль

                ServerHttpRequest mutatedRequest = exchange.getRequest() // беремо request
                        .mutate() // створюємо mutable копію
                        .header("X-User-Id", userId) // додаємо header userId
                        .header("X-User-Role", role) // додаємо header role
                        .build(); // будуємо новий request

                return chain.filter( // передаємо далі по chain
                        exchange.mutate().request(mutatedRequest).build() // підміняємо request
                );

            } catch (Exception e) { // якщо токен невалідний
                return unauthorized(exchange); // повертаємо 401
            }
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) { // helper метод
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // ставимо 401
        return exchange.getResponse().setComplete(); // завершуємо response
    }
}
