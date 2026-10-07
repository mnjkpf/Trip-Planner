package com.waylo.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Запит тікета від сервісу-власника (trip-service). */
public record TicketRequest(
        @NotBlank @Size(max = 200) String context,
        @NotBlank @Size(max = 64) String userId
) {}
