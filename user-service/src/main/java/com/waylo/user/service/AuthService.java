package com.waylo.user.service;

import com.waylo.user.domain.RefreshToken;
import com.waylo.user.domain.RoleName;
import com.waylo.user.domain.User;
import com.waylo.user.dto.AuthResponse;
import com.waylo.user.dto.LoginRequest;
import com.waylo.user.dto.LogoutRequest;
import com.waylo.user.dto.RefreshRequest;
import com.waylo.user.dto.RegisterRequest;
import com.waylo.user.error.ApiExceptions.EmailAlreadyUsedException;
import com.waylo.user.error.ApiExceptions.InvalidCredentialsException;
import com.waylo.user.repository.UserRepository;
import com.waylo.user.security.GoogleTokenVerifier;
import com.waylo.user.security.GoogleTokenVerifier.GoogleIdentity;
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
    private final GoogleTokenVerifier googleVerifier;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       GoogleTokenVerifier googleVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.googleVerifier = googleVerifier;
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
        if (!user.isEnabled() || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
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

    /**
     * Вхід/реєстрація через Google. Один метод на обидва випадки: якщо акаунт
     * із такою поштою вже є — просто логінимо (навіть якщо він був створений
     * локально: пошта підтверджена Google, тож це той самий власник). Якщо
     * нема — створюємо без пароля і з позначкою oauthProvider="google".
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleIdentity id = googleVerifier.verify(idToken);
        User user = userRepository.findByEmailIgnoreCase(id.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .id(UUID.randomUUID())
                        .email(id.email())
                        .passwordHash(null)              // пароля немає — вхід тільки через Google
                        .displayName(id.name())
                        .oauthProvider("google")
                        .role(RoleName.USER)
                        .enabled(true)
                        .build()));
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Акаунт вимкнено");
        }
        // Якщо акаунт був локальний, а зайшли через Google — лишаємо як є:
        // пароль продовжує працювати, Google стає другим способом входу.
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.issueAccessToken(user);
        String refresh = refreshTokenService.issue(user);
        return new AuthResponse(access, refresh, "Bearer", jwtService.accessTtlSeconds());
    }
}
