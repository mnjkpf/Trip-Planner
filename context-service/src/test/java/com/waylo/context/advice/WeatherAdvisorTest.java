package com.waylo.context.advice;

import com.waylo.context.weather.DailyWeather;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherAdvisorTest {

    private final WeatherAdvisor advisor = new WeatherAdvisor();

    private static final LocalDate MON = LocalDate.of(2026, 10, 12);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);

    private static PlannedPlace p(String name, String category) {
        return new PlannedPlace(name, category);
    }

    private static PlannedDay day(int index, LocalDate date, PlannedPlace... places) {
        return new PlannedDay(index, date, List.of(places));
    }

    private static DailyWeather rain(LocalDate date, double mm) {
        return new DailyWeather(date, 10.0, 18.0, mm);
    }

    @Test
    void rainyParkDay_andDryMuseumDay_suggestsSwap() {
        // Приклад із задачі: у середу дощ, а в плані парк і пляж; четвер — музеї.
        List<PlannedDay> days = List.of(
                day(3, WED, p("Villa Borghese", "PARK"), p("Lido", "BEACH"), p("Trattoria", "RESTAURANT")),
                day(4, THU, p("Vatican Museums", "MUSEUM"), p("Galleria", "MUSEUM"), p("Caffe", "CAFE")));

        List<WeatherAdvice> advice = advisor.advise(days, List.of(rain(WED, 9.4), rain(THU, 0.3)));

        assertEquals(1, advice.size());
        WeatherAdvice a = advice.get(0);
        assertEquals(WED, a.date());
        assertEquals(3, a.dayIndex());
        assertEquals(RainLevel.RAIN, a.level());
        assertEquals(List.of("Villa Borghese", "Lido"), a.outdoorPlaces());
        assertEquals(THU, a.swapDate());
        assertEquals(4, a.swapDayIndex());
        assertEquals(0.3, a.swapPrecipitationMm());
    }

    @Test
    void rainOnIndoorDay_isNotAWarning() {
        List<PlannedDay> days = List.of(
                day(1, MON, p("Louvre", "MUSEUM"), p("Bistro", "RESTAURANT")),
                day(2, TUE, p("Park", "PARK"), p("Beach", "BEACH")));

        assertTrue(advisor.advise(days, List.of(rain(MON, 20.0), rain(TUE, 0.0))).isEmpty());
    }

    @Test
    void drizzle_doesNotTriggerWarning() {
        List<PlannedDay> days = List.of(day(1, MON, p("A", "PARK"), p("B", "PARK")));

        assertTrue(advisor.advise(days, List.of(rain(MON, 3.0))).isEmpty());
    }

    @Test
    void heavyRain_warnsEvenForOneBeach_andWithoutSwapWhenNoDryDay() {
        List<PlannedDay> days = List.of(
                day(1, MON, p("Beach", "BEACH"), p("Cafe", "CAFE")),
                day(2, TUE, p("Museum", "MUSEUM")));

        List<WeatherAdvice> advice = advisor.advise(days, List.of(rain(MON, 15.0), rain(TUE, 6.0)));

        assertEquals(1, advice.size());
        assertEquals(RainLevel.HEAVY_RAIN, advice.get(0).level());
        assertNull(advice.get(0).swapDate(), "вівторок теж дощовий — міняти нема з чим");
    }

    @Test
    void swapMustRemoveAtLeastOneParkFromTheRain() {
        // Обидва дні однаково «вуличні» — обмін нічого не дасть.
        List<PlannedDay> days = List.of(
                day(1, MON, p("Park A", "PARK"), p("Sight", "ATTRACTION")),
                day(2, TUE, p("Park B", "PARK"), p("Sight 2", "ATTRACTION")));

        List<WeatherAdvice> advice = advisor.advise(days, List.of(rain(MON, 8.0), rain(TUE, 0.0)));

        assertEquals(1, advice.size());
        assertNull(advice.get(0).swapDate());
    }

    @Test
    void oneDryDay_goesToTheMostExposedRainyDay() {
        List<PlannedDay> days = List.of(
                day(1, MON, p("Park", "PARK"), p("Sight", "ATTRACTION")),                 // 3 бали
                day(2, TUE, p("Beach", "BEACH"), p("Park", "PARK"), p("Garden", "PARK")),  // 6 балів
                day(3, WED, p("Museum", "MUSEUM")));

        List<WeatherAdvice> advice = advisor.advise(days,
                List.of(rain(MON, 7.0), rain(TUE, 7.0), rain(WED, 0.5)));

        assertEquals(2, advice.size());
        assertEquals(MON, advice.get(0).date(), "результат відсортовано за датою");
        assertNull(advice.get(0).swapDate());
        assertEquals(WED, advice.get(1).swapDate());
    }

    @Test
    void daysWithoutForecast_areIgnored() {
        List<PlannedDay> days = List.of(
                day(1, MON, p("Park", "PARK"), p("Beach", "BEACH")),
                day(2, TUE, p("Museum", "MUSEUM")));

        // прогноз є лише на понеділок → сухого кандидата немає
        List<WeatherAdvice> advice = advisor.advise(days, List.of(rain(MON, 9.0)));

        assertEquals(1, advice.size());
        assertNull(advice.get(0).swapDate());
    }

    @Test
    void fingerprint_changesOnlyWhenAdviceChanges() {
        List<PlannedDay> days = List.of(
                day(1, MON, p("Park", "PARK"), p("Beach", "BEACH")),
                day(2, TUE, p("Museum", "MUSEUM")));

        String a = WeatherAdvisor.fingerprint(advisor.advise(days, List.of(rain(MON, 9.1), rain(TUE, 0.0))));
        String b = WeatherAdvisor.fingerprint(advisor.advise(days, List.of(rain(MON, 9.2), rain(TUE, 0.1))));
        String c = WeatherAdvisor.fingerprint(advisor.advise(days, List.of(rain(MON, 0.0), rain(TUE, 0.0))));

        assertEquals(a, b, "коливання прогнозу в межах міліметра — та сама порада");
        assertNotEquals(a, c);
        assertEquals("", c, "немає порад — порожній відбиток");
    }
}
