package com.travelplanner.user.web;

import com.travelplanner.user.dto.AuthResponse;
import com.travelplanner.user.dto.RegisterRequest;
import com.travelplanner.user.dto.UserResponse;
import com.travelplanner.user.service.AuthService;
import com.travelplanner.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    // Якщо заголовка нема — MissingRequestHeaderException → 401 (GlobalExceptionHandler).
    @GetMapping("/me")
    public UserResponse me(@RequestHeader("X-User-Id") UUID userId) {
        return userService.getById(userId);
    }
}
