package com.waylo.context.dto;

import com.waylo.context.weather.DailyWeather;

import java.util.List;

/**
 * Відповідь /api/context: сезон, підказка й погодовий прогноз (може бути порожнім).
 * climateHintCode — машинний код підказки (його перекладає фронт),
 * climateHint — той самий текст англійською як фолбек.
 */
public record ContextResponse(
        double destinationLat,
        double destinationLon,
        String season,
        String climateHint,
        String climateHintCode,
        List<DailyWeather> days
) {}
