package com.waylo.user.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waylo.user.domain.User;
import com.waylo.user.dto.ChangePasswordRequest;
import com.waylo.user.dto.UpdateProfileRequest;
import com.waylo.user.dto.UserLookupResponse;
import com.waylo.user.dto.UserResponse;
import com.waylo.user.error.ApiExceptions.UserNotFoundException;
import com.waylo.user.repository.RefreshTokenRepository;
import com.waylo.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Пошук за поштою — для запрошення в подорож з trip-service.
     * Віддаємо мінімум полів і 404 на невідому пошту: ендпоінт за JWT, але
     * перетворювати його на зручний інструмент перебору пошт теж не варто.
     */
    @Transactional(readOnly = true)
    public UserLookupResponse lookupByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .filter(User::isEnabled)
                .map(u -> new UserLookupResponse(u.getId(), u.getEmail(), u.getDisplayName()))
                .orElseThrow(() -> new UserNotFoundException("Користувача з такою поштою немає"));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return toResponse(require(id));
    }

    /** Оновлення косметичних полів (імʼя, обрана мова). Email і роль не чіпаємо. */
    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest req) {
        User u = require(id);
        if (req.displayName() != null) u.setDisplayName(req.displayName().isBlank() ? null : req.displayName().trim());
        if (req.preferredLanguage() != null) u.setPreferredLanguage(req.preferredLanguage().toLowerCase());
        return toResponse(u);
    }

    /**
     * Зміна паролю. Перевіряємо, що старий правильний (щоб не можна було
     * змінити з краденого access-токена без знання пароля). Після зміни
     * відкликаємо всі refresh-токени — усі сесії виходять, окрім поточного
     * access-токена, який і так закінчиться через 15 хв.
     */
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest req) {
        User u = require(id);
        if (u.getPasswordHash() == null) {
            // Акаунт створений через Google — локального пароля ніколи не було.
            throw new IllegalArgumentException("Акаунт входить через Google — пароля немає");
        }
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Старий пароль неправильний");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokenRepository.deleteByUserId(id);
    }

    /** Видалення акаунту: refresh-токени падають каскадом (FK ON DELETE CASCADE). */
    @Transactional
    public void deleteAccount(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("Користувача не знайдено");
        }
        userRepository.deleteById(id);
    }

    private User require(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Користувача не знайдено"));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(),
                u.getPreferredLanguage(), u.getOauthProvider(), u.getRole(), u.getCreatedAt());
    }
}
