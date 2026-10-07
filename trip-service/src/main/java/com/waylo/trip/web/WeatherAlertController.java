package com.waylo.trip.web;

import com.waylo.trip.dto.WeatherAlertResponse;
import com.waylo.trip.service.WeatherAlertService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Погодні попередження: лише читання й «сховати». Змінювати маршрут звідси не можна. */
@RestController
@RequestMapping("/api/trips/{id}/weather-alerts")
public class WeatherAlertController {

    private final WeatherAlertService weatherAlertService;

    public WeatherAlertController(WeatherAlertService weatherAlertService) {
        this.weatherAlertService = weatherAlertService;
    }

    @GetMapping
    public List<WeatherAlertResponse> list(@RequestHeader("X-User-Id") UUID userId,
                                           @PathVariable UUID id) {
        return weatherAlertService.list(userId, id);
    }

    @PostMapping("/{alertId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@RequestHeader("X-User-Id") UUID userId,
                        @PathVariable UUID id,
                        @PathVariable UUID alertId) {
        weatherAlertService.dismiss(userId, id, alertId);
    }
}
