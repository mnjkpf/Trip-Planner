package com.waylo.context.weather;

import java.time.LocalDate;

/** Денний прогноз. Поля Double (не double) — можуть бути відсутні у відповіді провайдера. */
public record DailyWeather(
        LocalDate date,
        Double tempMinC,
        Double tempMaxC,
        Double precipitationMm
) {}
