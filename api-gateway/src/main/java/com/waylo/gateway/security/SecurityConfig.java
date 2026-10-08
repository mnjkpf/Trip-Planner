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
 * перегляд маршруту за посиланням (/api/public/**), віддача картинок
 * (GET /api/media/**) і читання каталогу місць (GET /api/places/**) —
 * туди ходять гості без акаунта.
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
                        // Прев'ю маршруту — єдиний публічний POST: нічого не
                        // змінює на сервері, лише рахує й віддає відповідь.
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/public/plan").permitAll()
                        // Картинки віддаємо без токена: <img src> не вміє слати
                        // Authorization. Захист — невгадуваний UUID у шляху.
                        // Завантаження (POST /api/media/upload) лишається під автентифікацією.
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/media/**").permitAll()
                        // Каталог місць читається без акаунта: у відповідях немає
                        // нічого користувацького, а можливість подивитись, що вміє
                        // застосунок, до реєстрації — половина сенсу вітрини.
                        // Запис (POST /api/places/resolve-link) лишається під токеном.
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/places/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
