package com.travelplanner.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
                // preflight CORS має проходити без токена
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // публічні маршрути — без токена
                .pathMatchers(
                    "/api/auth/**",
                    "/api/user/register-user",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()
                // усе інше вимагає валідного токена.
                // За замовчуванням ЗАКРИТО: новий маршрут автоматично захищений,
                // забути причепити фільтр більше неможливо.
                .anyExchange().authenticated()
            )
            // RS256: gateway бере публічний ключ з JWKS user-service
            // (jwk-set-uri у application.properties) і сам перевіряє підпис.
            // Приватний ключ лишається тільки в user-service — підробити
            // токен не може жоден інший сервіс.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
