package com.waylo.offer.dto;

import java.util.List;

/**
 * Одна «варіація» рейсу від Google Flights. Може мати 1 або кілька сегментів
 * (стикування). Ціна — у валюті запиту ({@code currency}).
 */
public record FlightOffer(
    List<FlightSegment> segments,
    int totalDurationMinutes,
    int layoverCount,
    double price,
    String currency,
    String type,                 // "Round trip" або "One way" від SerpAPI
    String bookingToken,         // непрозорий токен SerpAPI — для «deep link» на booking
    Integer carbonEmissionsGrams // може бути null
) {}
