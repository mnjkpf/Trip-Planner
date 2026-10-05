package com.waylo.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(@NotBlank @Pattern(regexp = "EDITOR|VIEWER") String role) {}
