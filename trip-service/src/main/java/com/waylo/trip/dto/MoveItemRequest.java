package com.waylo.trip.dto;

/** Перенести пункт у день toDayIndex на позицію toOrder (1-based). */
public record MoveItemRequest(int toDayIndex, int toOrder) {}
