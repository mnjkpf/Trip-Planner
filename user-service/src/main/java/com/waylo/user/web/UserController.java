package com.waylo.user.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.waylo.user.dto.AuthResponse;
import com.waylo.user.dto.ChangePasswordRequest;
import com.waylo.user.dto.GoogleLoginRequest;
import com.waylo.user.dto.RegisterRequest;
import com.waylo.user.dto.UpdateProfileRequest;
import com.waylo.user.dto.UserLookupResponse;
import com.waylo.user.dto.UserResponse;
import com.waylo.user.service.AuthService;
import com.waylo.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    public UserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** Публічна реєстрація (gateway пускає без токена). */
    @PostMapping("/register-user")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    /**
     * Перетворює гостьовий акаунт на справжній. Під токеном гостя: id
     * користувача не змінюється, тож усі подорожі, створені до реєстрації,
     * лишаються на місці.
     */
    @PostMapping("/claim-guest")
    public AuthResponse claimGuest(@RequestHeader("X-User-Id") UUID userId,
                                   @Valid @RequestBody RegisterRequest req) {
        return authService.claimGuest(userId, req);
    }

    /** Те саме привласнення, але через Google Sign-In. */
    @PostMapping("/claim-guest-google")
    public AuthResponse claimGuestWithGoogle(@RequestHeader("X-User-Id") UUID userId,
                                             @Valid @RequestBody GoogleLoginRequest req) {
        return authService.claimGuestWithGoogle(userId, req.idToken());
    }

    /**
     * Пошук за поштою — ним користується trip-service, коли запрошують учасника.
     * Шлях під /api/user/**, тож gateway вимагає валідний токен.
     */
    @GetMapping("/lookup")
    public UserLookupResponse lookup(@RequestParam String email) {
        return userService.lookupByEmail(email);
    }

    /** Профіль поточного користувача; X-User-Id ставить gateway з JWT. */
    @GetMapping("/me")
    public UserResponse me(@RequestHeader("X-User-Id") UUID userId) {
        return userService.getById(userId);
    }

    /** Оновлення профілю (імʼя, обрана мова). */
    @PutMapping("/me")
    public UserResponse updateMe(@RequestHeader("X-User-Id") UUID userId,
                                 @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(userId, req);
    }

    /** Зміна паролю; після зміни всі refresh-токени відкликаються. */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader("X-User-Id") UUID userId,
                               @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(userId, req);
    }

    /** Видалення акаунту; FK-каскад прибирає усі залежні дані. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@RequestHeader("X-User-Id") UUID userId) {
        userService.deleteAccount(userId);
    }
}
