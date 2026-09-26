package com.travelplanner.context.dto;

import com.travelplanner.context.weather.DailyWeather;

import java.util.List;

/** Відповідь /api/context: сезон, підказка й погодовий прогноз (може бути порожнім). */
public record ContextResponse(
        double destinationLat,
        double destinationLon,
        String season,
        String climateHint,
        List<DailyWeather> days
) {}
