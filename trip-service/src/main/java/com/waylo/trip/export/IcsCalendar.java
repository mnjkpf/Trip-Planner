package com.waylo.trip.export;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Дані для .ics у вигляді, незалежному від того, звідки вони прийшли —
 * з маршруту власника чи з публічного посилання. Завдяки цьому IcsExporter
 * залишається чистою функцією й тестується без Spring.
 */
public record IcsCalendar(String name, List<Event> events) {

    /**
     * startTime == null → подія на весь день (у маршруті бувають пункти без часу).
     * uid має бути стабільним між експортами: інакше календар при повторному
     * імпорті створить дублі замість оновлення.
     */
    public record Event(
            String uid,
            String summary,
            LocalDate date,
            LocalTime startTime,
            int durationMinutes,
            Double lat,
            Double lon,
            String description
    ) {}
}
