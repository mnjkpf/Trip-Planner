package com.travelplanner.context.web;

import com.travelplanner.context.dto.ContextResponse;
import com.travelplanner.context.service.ContextService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Контекст для напряму й дат:
 *   GET /api/context?lat=41.9&lon=12.5&startDate=2026-10-01&endDate=2026-10-07
 * Синхронний виклик — planner звертається сюди під час побудови маршруту.
 */
@RestController
@RequestMapping("/api/context")
public class ContextController {

    private final ContextService contextService;

    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping
    public ContextResponse context(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return contextService.contextFor(lat, lon, startDate, endDate);
    }
}
