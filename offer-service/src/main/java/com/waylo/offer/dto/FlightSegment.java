package com.waylo.offer.dto;

/** Один сегмент перельоту (одна авіакомпанія, один рейс). */
public record FlightSegment(
    String departureAirport,     // IATA
    String departureAirportName,
    String departureTime,        // ISO-подібний рядок від SerpAPI, напр. "2026-10-15 10:30"
    String arrivalAirport,
    String arrivalAirportName,
    String arrivalTime,
    int durationMinutes,
    String airline,
    String airlineLogoUrl,
    String flightNumber,
    String travelClass,          // Economy / Business / тощо
    String airplane
) {}
