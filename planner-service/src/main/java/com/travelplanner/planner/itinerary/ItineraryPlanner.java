package com.travelplanner.planner.itinerary;

import com.travelplanner.planner.client.PlaceDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Чиста логіка побудови маршруту (без мережі й БД — легко юніт-тестити).
 * Кількість днів = включно між startDate і endDate. POI розкидаємо по днях
 * round-robin (рівномірно), у кожному дні нумеруємо порядок відвідування.
 * Це навмисно проста стратегія-каркас; оптимізацію за відстанню/годинами
 * додамо пізніше.
 */
@Component
public class ItineraryPlanner {

    public List<ItineraryDay> build(List<PlaceDto> places, LocalDate startDate, LocalDate endDate) {
        int days = (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
        if (days < 1) {
            days = 1;   // захист від некоректних дат — хоча б один день
        }

        List<List<ItineraryItem>> buckets = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            buckets.add(new ArrayList<>());
        }

        for (int i = 0; i < places.size(); i++) {
            PlaceDto p = places.get(i);
            List<ItineraryItem> dayBucket = buckets.get(i % days);
            int order = dayBucket.size() + 1;
            dayBucket.add(new ItineraryItem(p.id(), p.name(), p.lat(), p.lon(), order));
        }

        List<ItineraryDay> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            result.add(new ItineraryDay(i + 1, startDate.plusDays(i), buckets.get(i)));
        }
        return result;
    }
}
