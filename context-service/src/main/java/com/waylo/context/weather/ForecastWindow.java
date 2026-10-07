package com.waylo.context.weather;

import java.time.LocalDate;

/**
 * Відрізок дат, на який взагалі має сенс питати прогноз.
 *
 * Open-Meteo дає близько 16 днів наперед і на діапазон поза межами відповідає
 * помилкою — на ВЕСЬ запит. Тож без обрізання подорож «через два тижні на
 * п'ять днів» лишалася б зовсім без погоди, хоча перші її дні ще в горизонті.
 */
public record ForecastWindow(LocalDate from, LocalDate to) {

    public static final int HORIZON_DAYS = 16;

    /** Перетин [start, end] з [today, today + горизонт]. */
    public static ForecastWindow of(LocalDate start, LocalDate end, LocalDate today) {
        LocalDate horizon = today.plusDays(HORIZON_DAYS - 1L);
        LocalDate from = start.isBefore(today) ? today : start;
        LocalDate to = end.isAfter(horizon) ? horizon : end;
        return new ForecastWindow(from, to);
    }

    /** Порожнє вікно — подорож повністю в минулому або ще за горизонтом. */
    public boolean isEmpty() {
        return from.isAfter(to);
    }
}
