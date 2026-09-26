package com.travelplanner.user.service;

import com.travelplanner.user.domain.RefreshToken;
import com.travelplanner.user.domain.RoleName;
import com.travelplanner.user.domain.User;
import com.travelplanner.user.dto.AuthResponse;
import com.travelplanner.user.dto.LoginRequest;
import com.travelplanner.user.dto.LogoutRequest;
import com.travelplanner.user.dto.RefreshRequest;
import com.travelplanner.user.dto.RegisterRequest;
import com.travelplanner.user.error.ApiExceptions.EmailAlreadyUsedException;
import com.travelplanner.user.error.ApiExceptions.InvalidCredentialsException;
import com.travelplanner.user.repository.UserRepository;
import com.travelplanner.user.security.JwtService;
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
