package com.waylo.user.dto;

import java.util.UUID;

/**
 * Відповідь пошуку за поштою для ІНШИХ СЕРВІСІВ (запрошення в подорож).
 * Навмисно вужча за UserResponse: лише те, що треба показати в списку
 * учасників. Мова, спосіб входу й роль у системі — не чужа справа.
 */
public record UserLookupResponse(UUID id, String email, String displayName) {}
