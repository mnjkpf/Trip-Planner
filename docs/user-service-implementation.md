# user-service — повна інструкція з реалізації

> Перший мікросервіс. Тримає користувачів, підписує JWT приватним ключем
> (RS256) і віддає публічний ключ через JWKS, щоб gateway міг перевіряти
> токени. Без нього нічого не автентифікується — тому він перший.
>
> Стек: Java 21 · Spring Boot 4.1 · Spring Security 7 · JPA · PostgreSQL ·
> Flyway. Порт **8081**, база **userdb**.

---

## 0. Що вже готове і що робимо

Проект уже згенеровано з Initializr, залежності в `user-service/pom.xml` є
(web-mvc, data-jpa, flyway, postgresql, security, oauth2-resource-server,
validation, springdoc, lombok, testcontainers). **Нічого в pom додавати не
треба.** `oauth2-resource-server` нам потрібен не для валідації, а бо він
приносить бібліотеку `spring-security-oauth2-jose` — саме нею ми **підписуємо**
токени (`JwtEncoder`) і будуємо JWKS.

Ми напишемо: схему БД (Flyway), сутності, DTO, репозиторії, підписання JWT +
JWKS, сервіси реєстрації/логіну/refresh, контролери, обробку помилок і один
інтеграційний тест.

### Контракт із gateway (це вже працює на його боці)

- gateway бере публічний ключ з `http://localhost:8081/.well-known/jwks.json`
  і перевіряє підпис кожного токена сам;
- у токені gateway читає claim **`uid`** (UUID користувача) → кладе в заголовок
  `X-User-Id`, і claim **`role`** → у `X-User-Role`;
- усі downstream-сервіси (і сам user-service для `/me`) довіряють цим
  заголовкам. Тому user-service **не перевіряє токен повторно** — цим займається
  gateway.

Отже наші токени **обовʼязково** мусять містити claims `uid` і `role`, а JWKS
мусить віддаватися за точно цим шляхом.

---

## 1. Потік даних (щоб бачити ціль)

**Реєстрація / логін:**

```
Клієнт → POST /api/user/register-user (або /api/auth/login)
   → user-service: перевірка, хеш пароля (BCrypt), запис у БД
   → issue: access token (JWT RS256, 30 хв) + refresh token (opaque, 14 днів)
   → відповідь: { accessToken, refreshToken, tokenType, expiresIn }
```

**Запит до захищеного ресурсу:**

```
Клієнт → Authorization: Bearer <access>  → gateway
   gateway перевіряє підпис по JWKS → додає X-User-Id / X-User-Role
   → downstream-сервіс читає заголовки, токен уже не чіпає
```

**Оновлення токена:**

```
Клієнт → POST /api/auth/refresh { refreshToken }
   → user-service: хешує, шукає в БД, перевіряє (не прострочений / не відкликаний)
   → ротація: старий refresh відкликає, видає новий access + новий refresh
```

---

## 2. Структура файлів

Створюй усе під `user-service/src/main/java/com/waylo/user/`:

```
user/
├── UserServiceApplication.java        ← вже є, не чіпаємо
├── domain/
│   ├── User.java                      @Entity — користувач
│   ├── RoleName.java                  enum USER / ADMIN
│   └── RefreshToken.java              @Entity — refresh-токен (хеш)
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
├── dto/
│   ├── RegisterRequest.java           record
│   ├── LoginRequest.java              record
│   ├── RefreshRequest.java            record
│   ├── LogoutRequest.java             record
│   ├── AuthResponse.java              record
│   └── UserResponse.java              record
├── security/
│   ├── KeyConfig.java                 завантаження RSA-ключів з PEM
│   ├── JwtConfig.java                 JWKSource + JwtEncoder
│   ├── JwtService.java                випуск access-токена
│   ├── PasswordConfig.java            BCryptPasswordEncoder
│   └── SecurityConfig.java            фільтр-ланцюг (за gateway — permitAll)
├── service/
│   ├── AuthService.java               register / login / refresh / logout
│   ├── RefreshTokenService.java       генерація, хеш, ротація refresh
│   └── UserService.java               getById для /me
├── web/
│   ├── UserController.java            /api/user/**
│   ├── AuthController.java            /api/auth/**
│   └── JwksController.java            /.well-known/jwks.json
└── error/
    ├── ApiExceptions.java             власні винятки
    └── GlobalExceptionHandler.java    → RFC 7807 ProblemDetail

resources/
├── application.yml                    ← замінює application.properties (видали його)
└── db/migration/
    └── V1__init.sql                   схема БД
```

---

## 3. Конфіг: `resources/application.yml`

**Спочатку видали згенерований `application.properties`** — беремо yml, бо
конфіг тут вкладений і в properties був би нечитабельний.

```yaml
server:
  port: 8081

spring:
  application:
    name: user-service

  threads:
    virtual:
      enabled: true          # Java 21 — паралельність без реактивщини

  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/userdb
    username: ${POSTGRES_USER:tp}
    password: ${POSTGRES_PASSWORD:tp}

  jpa:
    hibernate:
      ddl-auto: validate     # схему веде ТІЛЬКИ Flyway, ніколи не update
    open-in-view: false
    properties:
      hibernate.jdbc.batch_size: 25

  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  jwt:
    issuer: ${JWT_ISSUER:http://localhost:8081}
    # Шляхи до ключів. Див. розділ 4 — де їх узяти і про робочу теку.
    private-key-location: ${JWT_PRIVATE_KEY_LOCATION:file:infra/keys/jwt-private.pem}
    public-key-location: ${JWT_PUBLIC_KEY_LOCATION:file:infra/keys/jwt-public.pem}
    access-ttl: ${JWT_ACCESS_TTL:30m}
    refresh-ttl: ${JWT_REFRESH_TTL:14d}
    key-id: waylo-key      # kid — має збігатися в токені й JWKS

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## 4. Ключі RS256 — зроби це ДО першого запуску

Приватний ключ підписує токени, публічний лежить у JWKS. Згенеруй пару один
раз у корені репозиторію:

```bash
mkdir -p infra/keys
# приватний, формат PKCS#8 (рядок BEGIN PRIVATE KEY)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out infra/keys/jwt-private.pem
# публічний, формат X.509/SPKI (рядок BEGIN PUBLIC KEY)
openssl rsa -pubout -in infra/keys/jwt-private.pem -out infra/keys/jwt-public.pem
```

На Windows запусти це в Git Bash. Тека `infra/keys/` уже в `.gitignore` —
ключі в git не потраплять.

> **ПАСТКА з робочою текою.** Шлях `file:infra/keys/...` відносний. Коли
> запускаєш сервіс із IDE, робоча тека за замовчуванням — тека модуля
> (`user-service/`), і ключі там не знайдуться. Два виходи:
>
> 1. У IntelliJ: Run Configuration → **Working directory** = корінь репо
>    (`D:\Trip-Planner`). Тоді `infra/keys/...` резолвиться.
> 2. Або пропиши абсолютний шлях у змінних оточення:
>    `JWT_PRIVATE_KEY_LOCATION=file:D:/Trip-Planner/infra/keys/jwt-private.pem`
>    (і так само public).

---

## 5. Схема БД: `resources/db/migration/V1__init.sql`

```sql
-- user-service: власники ідентичності. Жоден інший сервіс сюди не пише.

CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(120),
    role           VARCHAR(32)  NOT NULL DEFAULT 'USER',
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Пошта нечутлива до регістру: унікальність по lower(email),
-- інакше Roman@ і roman@ зареєструються обидва.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- Refresh-токени зберігаємо ЯК ХЕШ. Якщо базу зіллють — самі токени
-- з неї не дістануть.
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,     -- SHA-256 hex
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
```

Ролі тримаємо одним стовпцем `role` (USER/ADMIN) — цього достатньо, і воно
рівно збігається з одним claim `role` у токені. Якщо колись знадобиться
кілька ролей на користувача — додаси окрему таблицю міграцією V2.

---

## 6. Domain (сутності)

### `domain/RoleName.java`

```java
package com.waylo.user.domain;

public enum RoleName {
    USER,
    ADMIN
}
```

### `domain/User.java`

```java
package com.waylo.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    private UUID id;                    // генеруємо в коді (UUID.randomUUID())

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleName role;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

### `domain/RefreshToken.java`

```java
package com.waylo.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;          // SHA-256 hex від сирого токена

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
```

---

## 7. DTO (records)

Усі — прості Java-records у пакеті `dto`.

### `dto/RegisterRequest.java`

```java
package com.waylo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 120) String displayName
) {}
```

### `dto/LoginRequest.java`

```java
package com.waylo.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
```

### `dto/RefreshRequest.java`

```java
package com.waylo.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {}
```

### `dto/LogoutRequest.java`

```java
package com.waylo.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String refreshToken) {}
```

### `dto/AuthResponse.java`

```java
package com.waylo.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,     // завжди "Bearer"
        long expiresIn        // час життя access-токена в секундах
) {}
```

### `dto/UserResponse.java`

```java
package com.waylo.user.dto;

import com.waylo.user.domain.RoleName;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        RoleName role,
        Instant createdAt
) {}
```

---

## 8. Репозиторії

### `repository/UserRepository.java`

```java
package com.waylo.user.repository;

import com.waylo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
```

### `repository/RefreshTokenRepository.java`

```java
package com.waylo.user.repository;

import com.waylo.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
```

---

## 9. Security та JWT — серце сервісу

### `security/PasswordConfig.java`

```java
package com.waylo.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // сіль всередині кожного хешу
    }
}
```

### `security/KeyConfig.java` — читає PEM-ключі

```java
package com.waylo.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class KeyConfig {

    @Bean
    public RSAPrivateKey rsaPrivateKey(
            @Value("${app.jwt.private-key-location}") Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(is);   // BEGIN PRIVATE KEY
        }
    }

    @Bean
    public RSAPublicKey rsaPublicKey(
            @Value("${app.jwt.public-key-location}") Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return RsaKeyConverters.x509().convert(is);     // BEGIN PUBLIC KEY
        }
    }
}
```

### `security/JwtConfig.java` — JWKSource і JwtEncoder

```java
package com.waylo.user.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtConfig {

    // Одна RSA-пара з kid. Той самий kid потрапляє в заголовок токена
    // і в JWKS — по ньому gateway обирає ключ для перевірки.
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey,
            @Value("${app.jwt.key-id}") String keyId) {

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }
}
```

### `security/JwtService.java` — випуск access-токена

```java
package com.waylo.user.security;

import com.waylo.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String keyId;
    private final Duration accessTtl;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.key-id}") String keyId,
                      @Value("${app.jwt.access-ttl}") Duration accessTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.keyId = keyId;
        this.accessTtl = accessTtl;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())           // jti
                .claim("uid", user.getId().toString())      // ← gateway → X-User-Id
                .claim("role", user.getRole().name())       // ← gateway → X-User-Role
                .claim("email", user.getEmail())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
```

### `security/SecurityConfig.java`

user-service стоїть **за gateway**, який уже перевірив токен. Тому тут не
робимо повторну JWT-перевірку — вимикаємо CSRF, робимо stateless і пускаємо
все (реальні захищені ендпоінти читають `X-User-Id`, який ставить gateway).

```java
package com.waylo.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Доступ контролює gateway. Сервіс має бути недосяжний напряму
            // (у docker/k8s не публікуємо його порт назовні).
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

---

## 10. Сервіси (бізнес-логіка)

### `service/RefreshTokenService.java`

```java
package com.waylo.user.service;

import com.waylo.user.domain.RefreshToken;
import com.waylo.user.domain.User;
import com.waylo.user.error.ApiExceptions.InvalidRefreshTokenException;
import com.waylo.user.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    /** Ротація: відкликаємо старий, видаємо новий. Повертає новий сирий. */
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
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 недоступний", e);
        }
    }
}
```

### `service/AuthService.java`

```java
package com.waylo.user.service;

import com.waylo.user.domain.RefreshToken;
import com.waylo.user.domain.RoleName;
import com.waylo.user.domain.User;
import com.waylo.user.dto.*;
import com.waylo.user.error.ApiExceptions.EmailAlreadyUsedException;
import com.waylo.user.error.ApiExceptions.InvalidCredentialsException;
import com.waylo.user.repository.UserRepository;
import com.waylo.user.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new EmailAlreadyUsedException("Пошта вже зареєстрована");
        }
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .displayName(req.displayName())
                .role(RoleName.USER)
                .enabled(true)
                .build();
        userRepository.save(user);
        return issueTokens(user);          // авто-логін після реєстрації
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new InvalidCredentialsException("Невірна пошта або пароль"));
        if (!user.isEnabled() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // однакове повідомлення для «нема користувача» і «невірний пароль» —
            // щоб не підказувати, які пошти існують
            throw new InvalidCredentialsException("Невірна пошта або пароль");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken current = refreshTokenService.verify(req.refreshToken());
        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Користувача не існує"));
        String newRefresh = refreshTokenService.rotate(current, user);
        String access = jwtService.issueAccessToken(user);
        return new AuthResponse(access, newRefresh, "Bearer", jwtService.accessTtlSeconds());
    }

    @Transactional
    public void logout(LogoutRequest req) {
        refreshTokenService.revoke(req.refreshToken());
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.issueAccessToken(user);
        String refresh = refreshTokenService.issue(user);
        return new AuthResponse(access, refresh, "Bearer", jwtService.accessTtlSeconds());
    }
}
```

### `service/UserService.java`

```java
package com.waylo.user.service;

import com.waylo.user.dto.UserResponse;
import com.waylo.user.error.ApiExceptions.UserNotFoundException;
import com.waylo.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userRepository.findById(id)
                .map(u -> new UserResponse(
                        u.getId(), u.getEmail(), u.getDisplayName(),
                        u.getRole(), u.getCreatedAt()))
                .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));
    }
}
```

---

## 11. Контролери

### `web/UserController.java`

```java
package com.waylo.user.web;

import com.waylo.user.dto.RegisterRequest;
import com.waylo.user.dto.AuthResponse;
import com.waylo.user.dto.UserResponse;
import com.waylo.user.service.AuthService;
import com.waylo.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    public UserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    // Публічний (gateway пускає без токена)
    @PostMapping("/register-user")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    // Захищений: X-User-Id ставить gateway з валідованого токена.
    // Якщо заголовка нема — Spring кине помилку, яку ловимо як 401 (див. розділ 12).
    @GetMapping("/me")
    public UserResponse me(@RequestHeader("X-User-Id") UUID userId) {
        return userService.getById(userId);
    }
}
```

### `web/AuthController.java`

```java
package com.waylo.user.web;

import com.waylo.user.dto.*;
import com.waylo.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req);
    }
}
```

### `web/JwksController.java` — публічний ключ для gateway

```java
package com.waylo.user.web;

import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final JWKSource<SecurityContext> jwkSource;

    public JwksController(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    // Точно цей шлях чекає gateway (jwk-set-uri).
    // Віддаємо ТІЛЬКИ публічну частину — toPublicJWKSet().
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() throws Exception {
        var jwks = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
        return new JWKSet(jwks).toPublicJWKSet().toJSONObject();
    }
}
```

---

## 12. Обробка помилок → RFC 7807

### `error/ApiExceptions.java`

```java
package com.waylo.user.error;

public final class ApiExceptions {

    private ApiExceptions() {}

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String msg) { super(msg); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) { super(msg); }
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String msg) { super(msg); }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String msg) { super(msg); }
    }
}
```

### `error/GlobalExceptionHandler.java`

```java
package com.waylo.user.error;

import com.waylo.user.error.ApiExceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailUsed(EmailAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ProblemDetail handleAuth(RuntimeException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleNotFound(UserNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // Немає X-User-Id → запит прийшов не через gateway або без токена
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Потрібна автентифікація");
    }

    // Помилки валідації @Valid → 400 з переліком полів
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Помилка валідації");
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return pd;
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
```

---

## 13. Ендпоінти — підсумок

| Метод | Шлях | Доступ | Тіло запиту | Відповідь |
|---|---|---|---|---|
| POST | `/api/user/register-user` | публічний | `RegisterRequest` | 201 `AuthResponse` |
| POST | `/api/auth/login` | публічний | `LoginRequest` | 200 `AuthResponse` |
| POST | `/api/auth/refresh` | публічний | `RefreshRequest` | 200 `AuthResponse` |
| POST | `/api/auth/logout` | публічний | `LogoutRequest` | 204 |
| GET | `/api/user/me` | захищений (X-User-Id) | — | 200 `UserResponse` |
| GET | `/.well-known/jwks.json` | публічний | — | 200 JWK Set |

Приклад `AuthResponse`:

```json
{
  "accessToken": "eyJraWQiOiJ0cmF2ZWwtcGxhbm5lci1rZXkiLCJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "9c8f3b2a...url-safe...",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

---

## 14. Тест (Testcontainers)

Один інтеграційний тест, що піднімає реальний Postgres і проганяє
register → login → refresh. Клади в
`src/test/java/com/waylo/user/AuthFlowIT.java`.

Спершу — базовий клас із контейнером (можна використати згенерований
`TestcontainersConfiguration`, але для ясності ось явний варіант):

```java
package com.waylo.user;

import com.waylo.user.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // ключі для тесту — той самий infra/keys (робоча тека = корінь репо)
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void register_then_login_then_refresh() {
        // register
        var reg = rest.postForEntity("/api/user/register-user",
                new RegisterRequest("roman@example.com", "password123", "Roman"),
                AuthResponse.class);
        assertThat(reg.getStatusCode().value()).isEqualTo(201);
        assertThat(reg.getBody().accessToken()).isNotBlank();

        // login
        var login = rest.postForEntity("/api/auth/login",
                new LoginRequest("roman@example.com", "password123"),
                AuthResponse.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);

        // refresh
        var refresh = rest.postForEntity("/api/auth/refresh",
                new RefreshRequest(login.getBody().refreshToken()),
                AuthResponse.class);
        assertThat(refresh.getStatusCode().value()).isEqualTo(200);
        assertThat(refresh.getBody().refreshToken())
                .isNotEqualTo(login.getBody().refreshToken());   // ротація спрацювала
    }
}
```

> Звичайний Postgres-образ тут годиться — PostGIS user-service не потрібен.
> Для тесту ключі беруться з `infra/keys`, тож переконайся, що робоча тека
> тесту — корінь репо, або задай абсолютні шляхи через `@DynamicPropertySource`.

---

## 15. Як перевірити руками

```bash
# 1. Інфраструктура
docker compose up -d

# 2. Ключі (якщо ще не робив)
mkdir -p infra/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out infra/keys/jwt-private.pem
openssl rsa -pubout -in infra/keys/jwt-private.pem -out infra/keys/jwt-public.pem

# 3. Запусти user-service з IDE (робоча тека = корінь репо!), далі:

# реєстрація
curl -s -X POST http://localhost:8081/api/user/register-user \
  -H "Content-Type: application/json" \
  -d '{"email":"roman@example.com","password":"password123","displayName":"Roman"}'

# JWKS (те, що читає gateway)
curl -s http://localhost:8081/.well-known/jwks.json

# логін
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"roman@example.com","password":"password123"}'

# перевір токен: встав accessToken на https://jwt.io — маєш бачити
# claims uid, role, email, exp
```

Далі — через gateway (порт 8080), з `Authorization: Bearer <access>`:

```bash
curl -s http://localhost:8080/api/user/me -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Якщо повертає твого користувача — увесь ланцюг gateway → JWKS → X-User-Id
працює.

---

## 16. Порядок роботи (по кроках)

1. Видали `application.properties`, створи `application.yml` (розділ 3).
2. Згенеруй ключі, налаштуй робочу теку IDE = корінь репо (розділ 4).
3. `V1__init.sql` (розділ 5). Підніми `docker compose up -d`, переконайся,
   що `userdb` існує.
4. `domain/` → `repository/` → `dto/` (розділи 6–8) — це швидко.
5. `security/`: PasswordConfig → KeyConfig → JwtConfig → JwtService →
   SecurityConfig (розділ 9). Це найважче — не поспішай.
6. `service/`: RefreshTokenService → AuthService → UserService (розділ 10).
7. `web/`: контролери (розділ 11) і `error/` (розділ 12).
8. Запусти, перевір руками (розділ 15).
9. Додай тест (розділ 14), переконайся, що зелений.
10. Коміт: `feat(user-service): auth, JWT RS256, JWKS`.

Коли `/api/user/me` через gateway повертає користувача — user-service
готовий, і можна братися за place-service.

---

## Дрібні застереження

- **Claims `uid` і `role` — не забудь.** Gateway читає саме їх; без них
  `X-User-Id` буде порожній і downstream не знатиме, хто користувач.
- **kid у токені й JWKS має збігатися.** У нас обидва беруться з
  `app.jwt.key-id` — не задавай його у двох місцях по-різному.
- **Не логуй паролі й токени.** Навіть у debug.
- **`ddl-auto: validate`.** Якщо Hibernate свариться на неіснуючу колонку —
  це не помилка конфіга, а розбіжність сутності й міграції. Правологічно —
  правити міграцію, а не вмикати `update`.
- **Робоча тека.** 90% проблем на старті — ключі не знайшлися через відносний
  шлях. Якщо бачиш `FileNotFoundException: infra/keys/...` — це воно.
