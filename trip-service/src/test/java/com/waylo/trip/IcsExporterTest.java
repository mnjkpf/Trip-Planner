package com.waylo.trip;

import com.waylo.trip.export.IcsCalendar;
import com.waylo.trip.export.IcsExporter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Чистий тест формату RFC 5545 — без Spring і без БД. */
class IcsExporterTest {

    private final IcsExporter exporter = new IcsExporter();

    private String export(IcsCalendar.Event... events) {
        return exporter.export(new IcsCalendar("Trip to Dublin", List.of(events)));
    }

    private IcsCalendar.Event event(String name, LocalTime at, int minutes) {
        return new IcsCalendar.Event("id-1@waylo", name, LocalDate.of(2026, 10, 19),
                at, minutes, 53.35, -6.26, "ATTRACTION");
    }

    @Test
    void timedEvent_hasFloatingStartAndEnd() {
        String ics = export(event("Trinity College", LocalTime.of(9, 0), 90));

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertTrue(ics.contains("DTSTART:20261019T090000\r\n"), "плаваючий час — без Z і без TZID");
        assertTrue(ics.contains("DTEND:20261019T103000\r\n"), "кінець = початок + dwell");
        assertTrue(ics.contains("GEO:53.35;-6.26\r\n"));
        assertFalse(ics.contains("DTSTART:20261019T090000Z"), "Z зробив би час UTC");
    }

    @Test
    void itemWithoutTime_becomesAllDayEvent() {
        String ics = export(event("Trinity College", null, 0));

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261019\r\n"));
        // DTEND у all-day ексклюзивний: наступна доба, інакше подія зникне з календаря
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261020\r\n"));
    }

    @Test
    void specialCharactersAreEscaped() {
        String ics = export(new IcsCalendar.Event("u@waylo", "Pub; bar, grill\\here",
                LocalDate.of(2026, 10, 19), LocalTime.of(9, 0), 60, null, null, "line1\nline2"));

        assertTrue(ics.contains("SUMMARY:Pub\\; bar\\, grill\\\\here"));
        assertTrue(ics.contains("DESCRIPTION:line1\\nline2"));
    }

    @Test
    void longLinesAreFoldedByOctets_notCharacters() {
        // кирилиця: 2 байти на символ, тож перевіряємо саме байтовий ліміт
        String name = "Пам'ятка ".repeat(20);
        String ics = export(event(name, LocalTime.of(9, 0), 60));

        for (String line : ics.split("\r\n")) {
            int bytes = line.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(bytes <= 75, "рядок " + bytes + " октетів: " + line);
        }
        // згорнутий рядок читається назад як цілий
        String unfolded = ics.replace("\r\n ", "");
        assertTrue(unfolded.contains("SUMMARY:" + name.replace(",", "\\,")));
    }

    @Test
    void emptyItinerary_isStillValidCalendar() {
        String ics = exporter.export(new IcsCalendar("Empty", List.of()));

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    void uidIsStable_soReimportUpdatesInsteadOfDuplicating() {
        assertEquals("abc@waylo", IcsExporter.uid("abc"));
    }
}
