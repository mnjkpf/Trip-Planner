package com.waylo.context.service;

import com.waylo.context.dto.ContextResponse;
import com.waylo.context.season.Season;
import com.waylo.context.season.SeasonCalculator;
import com.waylo.context.weather.DailyWeather;
import com.waylo.context.weather.ForecastWindow;
import com.waylo.context.weather.WeatherClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Збирає контекст напряму: сезон (за місяцем/півкулею) + погодовий прогноз. */
@Service
public class ContextService {

    /** Запобіжник від безглуздо довгої смужки на дуже довгих подорожах. */
    private static final int MAX_DAYS = 30;

    private final SeasonCalculator seasonCalculator;
    private final WeatherClient weatherClient;

    public ContextService(SeasonCalculator seasonCalculator, WeatherClient weatherClient) {
        this.seasonCalculator = seasonCalculator;
        this.weatherClient = weatherClient;
    }

    public ContextResponse contextFor(double lat, double lon, LocalDate start, LocalDate end) {
        Season season = seasonCalculator.seasonFor(start, lat);
        // Питаємо лише ту частину дат, яку провайдер реально вміє: інакше
        // подорож, що виходить за горизонт, лишилась би взагалі без погоди.
        ForecastWindow window = ForecastWindow.of(start, end, LocalDate.now());
        List<DailyWeather> forecast = window.isEmpty()
                ? List.of()
                : weatherClient.forecast(lat, lon, window.from(), window.to());
        return new ContextResponse(lat, lon, season.name(),
                seasonCalculator.hint(season),
                seasonCalculator.hintCode(season).name(),
                allTripDays(start, end, forecast));
    }

    /**
     * Усі дні подорожі, а не лише ті, на які є прогноз. День за горизонтом
     * повертається з порожніми значеннями — фронт покаже «лише сезон».
     *
     * Так чесніше, ніж обривати смужку на півдорозі: інакше здається, що
     * решта днів кудись поділась, хоча насправді погоду на них просто ще
     * ніхто не знає.
     */
    private static List<DailyWeather> allTripDays(LocalDate start, LocalDate end, List<DailyWeather> forecast) {
        Map<LocalDate, DailyWeather> known = new HashMap<>();
        for (DailyWeather day : forecast) {
            if (day.date() != null) {
                known.put(day.date(), day);
            }
        }
        long length = Math.min(ChronoUnit.DAYS.between(start, end) + 1, MAX_DAYS);
        List<DailyWeather> days = new ArrayList<>();
        for (long i = 0; i < length; i++) {
            LocalDate date = start.plusDays(i);
            days.add(known.getOrDefault(date, new DailyWeather(date, null, null, null)));
        }
        return days;
    }
}
