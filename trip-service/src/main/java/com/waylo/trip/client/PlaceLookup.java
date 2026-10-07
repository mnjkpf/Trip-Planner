package com.waylo.trip.client;

/** Підмножина відповіді place-service, потрібна для фото пункту маршруту. */
public record PlaceLookup(String id, String name, String imageUrl) {}
