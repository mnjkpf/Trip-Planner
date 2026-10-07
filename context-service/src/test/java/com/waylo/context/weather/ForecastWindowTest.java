package com.waylo.context.weather;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Обрізання діапазону дат до горизонту прогнозу. */
class ForecastWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Test
    void tripInsideHorizon_isUnchanged() {
        ForecastWindow w = ForecastWindow.of(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12), TODAY);

        assertEquals(LocalDate.of(2026, 10, 8), w.from());
        assertEquals(LocalDate.of(2026, 10, 12), w.to());
        assertFalse(w.isEmpty());
    }

    @Test
    void tailBeyondHorizon_isCut_butStartIsKept() {
        // 19–23 жовтня: кінець за межами 16 днів, початок — ще ні.
        ForecastWindow w = ForecastWindow.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 23), TODAY);

        assertEquals(LocalDate.of(2026, 10, 19), w.from());
        assertEquals(LocalDate.of(2026, 10, 21), w.to(), "далі горизонту не питаємо");
        assertFalse(w.isEmpty(), "перші дні подорожі прогноз усе ще має");
    }

    @Test
    void startedTrip_countsFromToday() {
        ForecastWindow w = ForecastWindow.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9), TODAY);

        assertEquals(TODAY, w.from(), "минулі дні провайдер прогнозом не віддасть");
        assertEquals(LocalDate.of(2026, 10, 9), w.to());
    }

    @Test
    void fullyPastOrFullyBeyondHorizon_isEmpty() {
        assertTrue(ForecastWindow.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), TODAY).isEmpty());
        assertTrue(ForecastWindow.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), TODAY).isEmpty());
    }
}
