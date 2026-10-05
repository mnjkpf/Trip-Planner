package com.waylo.place.airport;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Автодоповнення аеропорту відльоту та підбір найближчого до координат.
 * Приклади:
 *   GET /api/places/airports?q=waw&limit=6
 *   GET /api/places/airports/nearest?lat=53.34&lon=-6.26&limit=3
 */
@RestController
@RequestMapping("/api/places/airports")
@Validated
public class AirportController {

    private final AirportService service;

    public AirportController(AirportService service) {
        this.service = service;
    }

    @GetMapping
    public List<Airport> suggest(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
        return service.suggest(q, limit);
    }

    @GetMapping("/nearest")
    public List<Airport> nearest(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit) {
        return service.nearest(lat, lon, limit);
    }
}
