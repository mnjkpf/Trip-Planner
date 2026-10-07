package com.waylo.context.season;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Чистий юніт-тест логіки сезону — без Spring. */
class SeasonCalculatorTest {

    private final SeasonCalculator calc = new SeasonCalculator();

    @Test
    void northernHemisphere_byMonth() {
        assertEquals(Season.WINTER, calc.seasonFor(LocalDate.of(2026, 1, 10), 50.0));
        assertEquals(Season.SPRING, calc.seasonFor(LocalDate.of(2026, 4, 1), 50.0));
        assertEquals(Season.SUMMER, calc.seasonFor(LocalDate.of(2026, 7, 15), 50.0));
        assertEquals(Season.AUTUMN, calc.seasonFor(LocalDate.of(2026, 10, 1), 50.0));
    }

    @Test
    void southernHemisphere_isOpposite() {
        // Сідней (lat < 0): липень = зима, січень = літо
        assertEquals(Season.WINTER, calc.seasonFor(LocalDate.of(2026, 7, 15), -33.9));
        assertEquals(Season.SUMMER, calc.seasonFor(LocalDate.of(2026, 1, 10), -33.9));
    }

    @Test
    void hint_isNonEmpty_forEverySeason() {
        for (Season s : Season.values()) {
            assertFalse(calc.hint(s).isBlank());
        }
    }
}
