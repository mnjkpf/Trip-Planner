package com.waylo.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive security для gateway (Spring Security 7 / WebFlux).
 *
 * Bearer-токени → stateless, тому csrf вимкнено.
 * Публічні: логін/реєстрація (/api/auth/**), JWKS, health/info/prometheus,
 * перегляд маршруту за посиланням (/api/public/**) і віддача картинок
 * (GET /api/media/**) — туди ходять гості без акаунта.
 * Решта /api/** — тільки з валідним JWT.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/api/auth/**", "/api/user/register-user", "/.well-known/jwks.json").permitAll()
                        .pathMatchers("/api/flights/**", "/api/hotels/**").permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/public/**").permitAll()
                        // Картинки віддаємо без токена: <img src> не вміє слати
                        // Authorization. Захист — невгадуваний UUID у шляху.
                        // Завантаження (POST /api/media/upload) лишається під автентифікацією.
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/media/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
