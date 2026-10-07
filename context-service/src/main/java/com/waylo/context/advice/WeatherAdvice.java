package com.waylo.context.advice;

import java.time.LocalDate;
import java.util.List;

/**
 * Одне попередження: у день {@code date} дощ, а в плані багато «під відкритим небом».
 * swap* — день, з яким варто ПОДУМАТИ про обмін (сухіший і з меншою кількістю прогулянок);
 * null, якщо такого дня в подорожі немає. Це лише порада: маршрут ніхто не змінює.
 */
public record WeatherAdvice(
        LocalDate date,
        int dayIndex,
        RainLevel level,
        double precipitationMm,
        List<String> outdoorPlaces,
        LocalDate swapDate,
        Integer swapDayIndex,
        Double swapPrecipitationMm
) {}
