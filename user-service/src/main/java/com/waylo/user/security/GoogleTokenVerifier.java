package com.waylo.user.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Перевіряє Google ID-token (JWT, підписаний Google) без зовнішніх бібліотек:
 * беремо NimbusJwtDecoder з публічними ключами Google і додатково звіряємо
 * issuer та audience (наш OAuth Client ID). Без цих двох перевірок будь-який
 * валідний Google-токен для чужого застосунку пройшов би як наш.
 *
 * Google видає токени з iss = "https://accounts.google.com" АБО
 * "accounts.google.com" — приймаємо обидва (так само робить офіційна бібліотека).
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> VALID_ISSUERS =
        List.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;
    private final String clientId;

    public GoogleTokenVerifier(@Value("${app.oauth.google.client-id:}") String clientId) {
        this.clientId = clientId;
        // Підпис перевіряє Nimbus за ключами з JWKS (кешує їх сам);
        // exp/nbf теж перевіряються дефолтним валідатором.
        this.decoder = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
    }

    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank();
    }

    /** Повертає розібраний токен або кидає {@link IllegalArgumentException}. */
    public GoogleIdentity verify(String idToken) {
        if (!isEnabled()) {
            throw new IllegalStateException("GOOGLE_OAUTH_CLIENT_ID не заданий");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException ex) {
            log.warn("google id_token не пройшов перевірку підпису: {}", ex.getMessage());
            throw new IllegalArgumentException("Недійсний Google-токен");
        }
        String iss = jwt.getClaimAsString("iss");
        if (iss == null || !VALID_ISSUERS.contains(iss)) {
            throw new IllegalArgumentException("Недійсний видавець токена");
        }
        List<String> aud = jwt.getAudience();
        if (aud == null || !aud.contains(clientId)) {
            // Токен виданий для ІНШОГО застосунку — приймати не можна.
            throw new IllegalArgumentException("Токен виданий не для цього застосунку");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("У токені немає email");
        }
        Boolean verified = jwt.getClaim("email_verified");
        if (verified != null && !verified) {
            throw new IllegalArgumentException("Пошта не підтверджена в Google");
        }
        return new GoogleIdentity(
            email,
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("sub")
        );
    }

    /** Мінімум, що нам потрібен із Google-профілю. */
    public record GoogleIdentity(String email, String name, String googleSub) {}
}
