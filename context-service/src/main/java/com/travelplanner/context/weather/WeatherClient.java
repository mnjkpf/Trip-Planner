package com.travelplanner.context.weather;

import java.time.LocalDate;
import java.util.List;

/** Абстракція над джерелом погоди — щоб у тестах підмінити заглушкою (без мережі). */
public interface WeatherClient {
    List<DailyWeather> forecast(double lat, double lon, LocalDate start, LocalDate end);
}
