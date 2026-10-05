package com.waylo.planner.itinerary;

import com.waylo.planner.client.PlaceDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Побудова маршруту (чиста логіка — без мережі й БД).
 * Кроки:
 *   1) POI рівномірно по днях (round-robin за кількістю днів включно),
 *      з обмеженням місць на день за темпом (pace), якщо його вибрали;
 *   2) у межах дня — порядок nearest-neighbor від першої точки, щоб менше ходити;
 *   3) розклад: день з dayStart (дефолт 09:00), час пішки між точками з haversine
 *      (4.5 км/год), тривалість перебування (dwell) — за категорією.
 *
 * Побажання опційні: pace == null означає «без обмежень», dayStart == null — 09:00.
 */
@Component
public class ItineraryPlanner {

    private static final double WALK_KMH = 4.5;
    private static final LocalTime DAY_START = LocalTime.of(9, 0);

    /** Скільки місць на день для кожного темпу. */
    private static final int RELAXED_PER_DAY = 3;
    private static final int BALANCED_PER_DAY = 5;
    private static final int PACKED_PER_DAY = 7;

    /** Без побажань — поведінка за замовчуванням. */
    public List<ItineraryDay> build(List<PlaceDto> places, LocalDate startDate, LocalDate endDate) {
        return build(places, startDate, endDate, null, null);
    }

    public List<ItineraryDay> build(List<PlaceDto> places,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    String pace,
                                    LocalTime dayStart) {
        int days = (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
        if (days < 1) {
            days = 1;
        }
        int maxPerDay = maxPerDay(pace);
        LocalTime start = dayStart == null ? DAY_START : dayStart;

        List<List<PlaceDto>> buckets = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            buckets.add(new ArrayList<>());
        }
        // Місця вже відсортовані за інтересами (найцікавіші спереду), тож round-robin
        // розкладає найкращі по різних днях, а зайві «хвости» відсікає ліміт темпу.
        int capacity = maxPerDay == Integer.MAX_VALUE ? Integer.MAX_VALUE : days * maxPerDay;
        int placed = 0;
        for (int i = 0; i < places.size() && placed < capacity; i++) {
            List<PlaceDto> bucket = buckets.get(i % days);
            if (bucket.size() >= maxPerDay) {
                continue;
            }
            bucket.add(places.get(i));
            placed++;
        }

        List<ItineraryDay> result = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            List<ItineraryItem> items = schedule(nearestNeighbor(buckets.get(d)), start);
            result.add(new ItineraryDay(d + 1, startDate.plusDays(d), items));
        }
        return result;
    }

    /** Ліміт місць на день за темпом. Невідомий або відсутній темп — без ліміту. */
    private static int maxPerDay(String pace) {
        if (pace == null || pace.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return switch (pace.trim().toUpperCase(Locale.ROOT)) {
            case "RELAXED" -> RELAXED_PER_DAY;
            case "BALANCED" -> BALANCED_PER_DAY;
            case "PACKED" -> PACKED_PER_DAY;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Порядок відвідування: від першої точки щоразу беремо найближчу невідвідану. */
    private List<PlaceDto> nearestNeighbor(List<PlaceDto> pts) {
        if (pts.size() <= 2) {
            return new ArrayList<>(pts);
        }
        List<PlaceDto> remaining = new ArrayList<>(pts);
        List<PlaceDto> route = new ArrayList<>();
        PlaceDto cur = remaining.remove(0);
        route.add(cur);
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                double dd = dist(cur, remaining.get(i));
                if (dd < bestD) {
                    bestD = dd;
                    best = i;
                }
            }
            cur = remaining.remove(best);
            route.add(cur);
        }
        return route;
    }

    /** Розклад дня: dayStart + (час пішки) → перебування → наступна точка. */
    private List<ItineraryItem> schedule(List<PlaceDto> ordered, LocalTime dayStart) {
        List<ItineraryItem> items = new ArrayList<>();
        LocalTime cursor = dayStart;
        PlaceDto prev = null;
        for (int i = 0; i < ordered.size(); i++) {
            PlaceDto p = ordered.get(i);
            int travel = prev == null ? 0 : walkMinutes(dist(prev, p));
            LocalTime start = cursor.plusMinutes(travel);
            int dwell = dwellFor(p.category());
            LocalTime end = start.plusMinutes(dwell);
            items.add(new ItineraryItem(
                    p.id(), p.name(), p.category(), p.lat(), p.lon(),
                    i + 1, travel, dwell, hm(start), hm(end)));
            cursor = end;
            prev = p;
        }
        return items;
    }

    private static int dwellFor(String category) {
        if (category == null) {
            return 60;
        }
        return switch (category) {
            case "MUSEUM" -> 120;
            case "RESTAURANT" -> 90;
            case "CAFE" -> 45;
            case "PARK" -> 75;
            case "HOTEL" -> 30;
            default -> 60;    // ATTRACTION, BAR, SHOP, BEACH, OTHER
        };
    }

    private static int walkMinutes(double meters) {
        if (meters <= 0) {
            return 0;
        }
        int m = (int) Math.round(meters / 1000.0 * 60.0 / WALK_KMH);
        return Math.max(m, 1);
    }

    private static String hm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static double dist(PlaceDto a, PlaceDto b) {
        return haversine(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /** Відстань по великому колу в метрах. */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(x));
    }
}
