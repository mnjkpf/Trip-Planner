package com.travelplanner.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity  // важливо: WebFlux, не звичайний WebSecurity!
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // публічні маршрути — без токена
                .pathMatchers(
                    "/api/auth/**",
                    "/api/user/register-user",
                    "/actuator/**"
                ).permitAll()
                // всі інші — поки теж дозволяємо
                // Gateway сам перевіряє через JwtAuthFilter
                .anyExchange().permitAll()
            );
        return http.build();
    }
}