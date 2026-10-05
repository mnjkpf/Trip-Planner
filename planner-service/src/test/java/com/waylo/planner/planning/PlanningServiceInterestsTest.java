package com.waylo.planner.planning;

import com.waylo.planner.client.PlaceDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Пріоритезація POI за інтересами — чиста функція, без Spring. */
class PlanningServiceInterestsTest {

    private static PlaceDto p(String id, String category) {
        return new PlaceDto(id, "Place " + id, category, 41.9, 12.5);
    }

    private final List<PlaceDto> places = List.of(
            p("1", "ATTRACTION"),
            p("2", "PARK"),
            p("3", "MUSEUM"),
            p("4", "PARK"),
            p("5", "BAR"));

    @Test
    void matchingCategoriesComeFirst_keepingRelativeOrder() {
        List<PlaceDto> out = PlanningService.prioritizeByInterests(places, List.of("PARK"));

        assertEquals(List.of("2", "4", "1", "3", "5"), out.stream().map(PlaceDto::id).toList());
    }

    @Test
    void severalInterests_areAllPrioritized() {
        List<PlaceDto> out = PlanningService.prioritizeByInterests(places, List.of("museum", " park "));

        assertEquals(List.of("2", "3", "4", "1", "5"), out.stream().map(PlaceDto::id).toList());
    }

    @Test
    void noInterests_orNoMatch_leavesListUntouched() {
        assertSame(places, PlanningService.prioritizeByInterests(places, List.of()));
        assertSame(places, PlanningService.prioritizeByInterests(places, null));
        assertSame(places, PlanningService.prioritizeByInterests(places, List.of("BEACH")));
    }
}
