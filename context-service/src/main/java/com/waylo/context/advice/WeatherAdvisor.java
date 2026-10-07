package com.waylo.context.advice;

import com.waylo.context.weather.DailyWeather;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Порівнює прогноз із маршрутом і радить, які дні варто поміняти місцями.
 * Чиста функція без Spring-залежностей і мережі — тому вся логіка покрита
 * простими юніт-тестами, а job лише постачає їй дані.
 *
 * Модель навмисно проста й пояснювана:
 *  - кожен пункт має бали «під відкритим небом»: парк і пляж — 2, визначна
 *    пам'ятка — 1 (часто це площа чи фонтан, але буває й собор), решта — 0;
 *  - день ризиковий, якщо дощ ≥ 5 мм і балів ≥ 3, або злива ≥ 12 мм і балів ≥ 2
 *    (тобто навіть один парк чи пляж);
 *  - кандидат на обмін — сухий день (< 2 мм), у якому балів хоча б на 2 менше:
 *    обмін має прибрати з-під дощу щонайменше один парк чи пляж;
 *  - кожен сухий день віддаємо лише одному дощовому, починаючи з найбільш
 *    «вуличного», щоб не радити двом дням обмінятися з тим самим.
 */
@Component
public class WeatherAdvisor {

    static final double RAIN_MM = 5.0;
    static final double HEAVY_RAIN_MM = 12.0;
    static final double DRY_MM = 2.0;
    /** Мінімальний виграш обміну в балах — один парк або пляж. */
    static final int MIN_GAIN = 2;
    /** Скільки назв показувати в тексті попередження. */
    private static final int MAX_NAMES = 6;

    static int exposure(String category) {
        if (category == null) {
            return 0;
        }
        return switch (category) {
            case "PARK", "BEACH" -> 2;
            case "ATTRACTION" -> 1;
            default -> 0;
        };
    }

    static int exposure(PlannedDay day) {
        int sum = 0;
        for (PlannedPlace p : places(day)) {
            sum += exposure(p.category());
        }
        return sum;
    }

    static boolean isRisky(int exposure, double mm) {
        if (mm >= HEAVY_RAIN_MM) {
            return exposure >= 2;
        }
        return mm >= RAIN_MM && exposure >= 3;
    }

    /**
     * @param days     дні маршруту (вже без минулих)
     * @param forecast денний прогноз; дні без прогнозу просто пропускаємо
     * @return попередження, відсортовані за датою (порожньо — все гаразд)
     */
    public List<WeatherAdvice> advise(List<PlannedDay> days, List<DailyWeather> forecast) {
        Map<LocalDate, Double> rain = new HashMap<>();
        for (DailyWeather w : forecast) {
            if (w.date() != null && w.precipitationMm() != null) {
                rain.put(w.date(), w.precipitationMm());
            }
        }

        List<PlannedDay> known = days.stream()
                .filter(d -> d.date() != null && rain.containsKey(d.date()))
                .toList();

        List<PlannedDay> risky = known.stream()
                .filter(d -> isRisky(exposure(d), rain.get(d.date())))
                .sorted(Comparator.<PlannedDay>comparingInt(d -> exposure(d)).reversed()
                        .thenComparing((PlannedDay d) -> rain.get(d.date()), Comparator.reverseOrder())
                        .thenComparing(PlannedDay::date))
                .toList();

        List<PlannedDay> dryPool = new ArrayList<>(known.stream()
                .filter(d -> rain.get(d.date()) < DRY_MM)
                .toList());

        List<WeatherAdvice> result = new ArrayList<>();
        for (PlannedDay day : risky) {
            double mm = rain.get(day.date());
            PlannedDay swap = bestSwap(day, dryPool, rain);
            if (swap != null) {
                dryPool.remove(swap);
            }
            result.add(new WeatherAdvice(
                    day.date(),
                    day.dayIndex(),
                    mm >= HEAVY_RAIN_MM ? RainLevel.HEAVY_RAIN : RainLevel.RAIN,
                    round1(mm),
                    outdoorNames(day),
                    swap == null ? null : swap.date(),
                    swap == null ? null : swap.dayIndex(),
                    swap == null ? null : round1(rain.get(swap.date()))));
        }
        result.sort(Comparator.comparing(WeatherAdvice::date));
        return result;
    }

    /**
     * Відбиток набору порад: job порівнює його з попереднім і шле подію в Kafka
     * лише тоді, коли порада справді змінилась. Опади округлюємо до мм, щоб
     * дрібні коливання прогнозу не породжували потік однакових попереджень.
     */
    public static String fingerprint(List<WeatherAdvice> advice) {
        StringBuilder sb = new StringBuilder();
        for (WeatherAdvice a : advice) {
            sb.append(a.date()).append(':')
                    .append(a.level()).append(':')
                    .append(Math.round(a.precipitationMm())).append(':')
                    .append(a.swapDate()).append(':')
                    .append(String.join(",", a.outdoorPlaces()))
                    .append(';');
        }
        return sb.toString();
    }

    private static PlannedDay bestSwap(PlannedDay wet, List<PlannedDay> pool, Map<LocalDate, Double> rain) {
        int wetExposure = exposure(wet);
        PlannedDay best = null;
        int bestGain = 0;
        for (PlannedDay candidate : pool) {
            if (candidate.date().equals(wet.date())) {
                continue;
            }
            int gain = wetExposure - exposure(candidate);
            if (gain < MIN_GAIN) {
                continue;
            }
            if (best == null || gain > bestGain
                    || (gain == bestGain && preferable(candidate, best, wet, rain))) {
                best = candidate;
                bestGain = gain;
            }
        }
        return best;
    }

    /** При рівному виграші — сухіший день, далі ближчий до дощового (менше ламає логістику). */
    private static boolean preferable(PlannedDay a, PlannedDay b, PlannedDay wet, Map<LocalDate, Double> rain) {
        int byRain = Double.compare(rain.get(a.date()), rain.get(b.date()));
        if (byRain != 0) {
            return byRain < 0;
        }
        return distance(a, wet) < distance(b, wet);
    }

    private static long distance(PlannedDay a, PlannedDay b) {
        return Math.abs(ChronoUnit.DAYS.between(a.date(), b.date()));
    }

    private static List<String> outdoorNames(PlannedDay day) {
        Set<String> names = new LinkedHashSet<>();
        for (PlannedPlace p : places(day)) {
            if (exposure(p.category()) > 0 && p.name() != null && !p.name().isBlank()) {
                names.add(p.name());
            }
        }
        return names.stream().limit(MAX_NAMES).toList();
    }

    private static List<PlannedPlace> places(PlannedDay day) {
        return day.places() == null ? List.of() : day.places();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
