package com.waylo.trip.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Запрошення за поштою. OWNER не запрошуємо — власник один і це автор. */
public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "EDITOR|VIEWER") String role
) {}
