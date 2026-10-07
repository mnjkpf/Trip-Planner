package com.waylo.planner.itinerary;

import com.waylo.planner.client.PlaceDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чистий юніт-тест логіки побудови маршруту — без Spring/Kafka/мережі.
 */
class ItineraryPlannerTest {

    private final ItineraryPlanner planner = new ItineraryPlanner();

    private List<PlaceDto> places(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> new PlaceDto("id-" + i, "Place " + i, "ATTRACTION", 10.0 + i, 20.0 + i, null))
                .toList();
    }

    @Test
    void distributesPlacesRoundRobin_acrossInclusiveDays() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 3);           // 3 дні включно
        List<ItineraryDay> days = planner.build(places(5), start, end);

        assertEquals(3, days.size());
        // 5 POI по 3 днях round-robin: день1=2, день2=2, день3=1
        assertEquals(2, days.get(0).items().size());
        assertEquals(2, days.get(1).items().size());
        assertEquals(1, days.get(2).items().size());

        // дати й номери днів послідовні
        assertEquals(1, days.get(0).dayNumber());
        assertEquals(start, days.get(0).date());
        assertEquals(end, days.get(2).date());

        // порядок у межах дня нумерується з 1
        assertEquals(1, days.get(0).items().get(0).order());
        assertEquals(2, days.get(0).items().get(1).order());
    }

    @Test
    void moreDaysThanPlaces_leavesTrailingDaysEmpty() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 5);           // 5 днів
        List<ItineraryDay> days = planner.build(places(2), start, end);

        assertEquals(5, days.size());
        assertEquals(1, days.get(0).items().size());
        assertEquals(1, days.get(1).items().size());
        assertTrue(days.get(2).items().isEmpty());
        assertTrue(days.get(4).items().isEmpty());
    }

    @Test
    void noPlaces_stillReturnsDayBuckets() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 2);
        List<ItineraryDay> days = planner.build(List.of(), start, end);

        assertEquals(2, days.size());
        assertTrue(days.get(0).items().isEmpty());
        assertTrue(days.get(1).items().isEmpty());
    }

    @Test
    void sameStartAndEnd_isSingleDay_allPlacesThatDay() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        List<ItineraryDay> days = planner.build(places(3), day, day);

        assertEquals(1, days.size());
        assertEquals(3, days.get(0).items().size());
    }

    @Test
    void pace_capsPlacesPerDay() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 2);           // 2 дні
        // 20 POI, спокійний темп → не більше 3 на день
        List<ItineraryDay> days = planner.build(places(20), start, end, "RELAXED", null);

        assertEquals(2, days.size());
        assertEquals(3, days.get(0).items().size());
        assertEquals(3, days.get(1).items().size());
    }

    @Test
    void unknownOrNullPace_meansNoCap() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        assertEquals(9, planner.build(places(9), day, day, null, null).get(0).items().size());
        assertEquals(9, planner.build(places(9), day, day, "WHATEVER", null).get(0).items().size());
    }

    @Test
    void dayStartTime_shiftsFirstItem() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        assertEquals("09:00", planner.build(places(1), day, day).get(0).items().get(0).plannedStart());
        assertEquals("11:00", planner.build(places(1), day, day, null, LocalTime.of(11, 0))
                .get(0).items().get(0).plannedStart());
    }
}
