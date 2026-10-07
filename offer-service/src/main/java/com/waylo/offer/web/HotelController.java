package com.waylo.offer.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waylo.offer.dto.HotelSearchResponse;
import com.waylo.offer.service.HotelService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Приклад:
 *   GET /api/hotels?q=Dublin&checkIn=2026-10-15&checkOut=2026-10-22&adults=2
 */
@RestController
@RequestMapping("/api/hotels")
@Validated
public class HotelController {

    private final HotelService service;

    public HotelController(HotelService service) {
        this.service = service;
    }

    @GetMapping
    public HotelSearchResponse search(
            @RequestParam @NotBlank String q,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "1") @Min(1) @Max(9) int adults,
            @RequestParam(required = false) String currency) {
        return service.search(q, checkIn, checkOut, adults, currency);
    }
}
