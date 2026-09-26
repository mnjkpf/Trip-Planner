package com.travelplanner.user.service;

import com.travelplanner.user.domain.RefreshToken;
import com.travelplanner.user.domain.User;
import com.travelplanner.user.error.ApiExceptions.InvalidRefreshTokenException;
import com.travelplanner.user.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
        this.repository = repository;
        this.refreshTtl = refreshTtl;
    }

    /** Генерує НОВИЙ сирий refresh-токен, зберігає його ХЕШ, повертає сирий. */
    @Transactional
    public String issue(User user) {
        String raw = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(sha256(raw))
                .expiresAt(Instant.now().plus(refreshTtl))
                .build();
        repository.save(entity);
        return raw;                 // клієнту віддаємо сирий, у БД лежить хеш
    }

    /** Перевіряє сирий токен, повертає його запис. Кидає виняток, якщо невалідний. */
    @Transactional(readOnly = true)
    public RefreshToken verify(String raw) {
        RefreshToken token = repository.findByTokenHash(sha256(raw))
                .orElseThrow(() -> new InvalidRefreshTokenException("Невідомий refresh-токен"));
        if (!token.isActive()) {
            throw new InvalidRefreshTokenException("Refresh-токен прострочений або відкликаний");
        }
        return token;
    }

    /** Ротація: відкликаємо старий, видаємо новий. Повертає новий сирий токен. */
    @Transactional
    public String rotate(RefreshToken current, User user) {
        current.setRevokedAt(Instant.now());
        repository.save(current);
        return issue(user);
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(sha256(raw)).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            repository.save(t);
        });
    }

    // ---- helpers ----

    private String generateRawToken() {
        byte[] bytes = new byte[32];                  // 256 біт ентропії
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступний", e);
        }
    }
}
