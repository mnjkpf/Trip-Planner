package com.waylo.offer.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waylo.offer.dto.FlightSearchResponse;
import com.waylo.offer.service.FlightService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Приклад:
 *   GET /api/flights?from=KBP&to=DUB&depart=2026-10-15&returnDate=2026-10-22&adults=1
 * Return-дата опційна (one-way), валюта за замовчуванням EUR.
 */
@RestController
@RequestMapping("/api/flights")
@Validated
public class FlightController {

    private final FlightService service;

    public FlightController(FlightService service) {
        this.service = service;
    }

    @GetMapping
    public FlightSearchResponse search(
            @RequestParam @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String from,
            @RequestParam @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @RequestParam(defaultValue = "1") @Min(1) @Max(9) int adults,
            @RequestParam(required = false) String currency) {
        return service.search(from, to, depart, returnDate, adults, currency);
    }
}
