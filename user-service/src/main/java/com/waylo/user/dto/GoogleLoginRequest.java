package com.waylo.user.dto;

import jakarta.validation.constraints.NotBlank;

/** ID-token, який Google Identity Services віддає фронту після входу. */
public record GoogleLoginRequest(@NotBlank String idToken) {}
