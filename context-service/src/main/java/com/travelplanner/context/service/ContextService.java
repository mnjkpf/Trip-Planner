package com.travelplanner.context.service;

import com.travelplanner.context.dto.ContextResponse;
import com.travelplanner.context.season.Season;
import com.travelplanner.context.season.SeasonCalculator;
import com.travelplanner.context.weather.DailyWeather;
import com.travelplanner.context.weather.WeatherClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** Збирає контекст напряму: сезон (за місяцем/півкулею) + погодовий прогноз. */
@Service
public class ContextService {

    private final SeasonCalculator seasonCalculator;
    private final WeatherClient weatherClient;

    public ContextService(SeasonCalculator seasonCalculator, WeatherClient weatherClient) {
        this.seasonCalculator = seasonCalculator;
        this.weatherClient = weatherClient;
    }

    public ContextResponse contextFor(double lat, double lon, LocalDate start, LocalDate end) {
        Season season = seasonCalculator.seasonFor(start, lat);
        List<DailyWeather> days = weatherClient.forecast(lat, lon, start, end);
        return new ContextResponse(lat, lon, season.name(), seasonCalculator.hint(season), days);
    }
}
