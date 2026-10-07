package com.waylo.trip.dto;

/**
 * Часткове оновлення пункту. null у полі = не чіпати; порожній рядок у plannedStart/note = очистити.
 */
public record PatchItemRequest(String plannedStart, String note, Boolean locked) {}
