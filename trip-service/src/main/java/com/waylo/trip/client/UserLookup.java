package com.waylo.trip.client;

import java.util.UUID;

/** Підмножина відповіді user-service, потрібна для списку учасників. */
public record UserLookup(UUID id, String email, String displayName) {}
