package com.waylo.user.web;

import com.waylo.user.dto.AuthResponse;
import com.waylo.user.dto.GoogleLoginRequest;
import com.waylo.user.dto.LoginRequest;
import com.waylo.user.dto.LogoutRequest;
import com.waylo.user.dto.RefreshRequest;
import com.waylo.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /** Вхід/реєстрація через Google Sign-In (ID-token від Google Identity Services). */
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest req) {
        return authService.loginWithGoogle(req.idToken());
    }

    /**
     * Гостьовий вхід без реєстрації — щоб можна було спланувати подорож одразу.
     * Повертає такі самі токени, як звичайний логін; акаунт позначений як
     * тимчасовий і перетворюється на справжній через /api/user/claim-guest.
     */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse guest() {
        return authService.loginAsGuest();
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
