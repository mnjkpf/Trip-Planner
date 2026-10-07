package com.waylo.trip.export;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Генератор iCalendar (RFC 5545). Пишемо руками, без бібліотеки: формат тут
 * потрібен у мінімальному обсязі, зате видно всі три речі, на яких зазвичай
 * ламаються саморобні .ics — екранування, згортання довгих рядків і CRLF.
 *
 * ЧАС ПЛАВАЮЧИЙ (floating): DTSTART без Z і без TZID. Це свідомо — ми не знаємо
 * таймзони міста призначення (place-service віддає лише координати), а для
 * маршруту подорожі «09:00 за місцевим часом там, де ти будеш» — саме та
 * семантика, яку описує floating time в RFC 5545 §3.3.5.
 */
@Component
public class IcsExporter {

    private static final String CRLF = "\r\n";
    private static final String PRODID = "-//Waylo//Trip Planner//EN";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public String export(IcsCalendar calendar) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:" + PRODID);
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(calendar.name()));

        String stamp = UTC_STAMP.format(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        for (IcsCalendar.Event e : calendar.events()) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + e.uid());
            line(out, "DTSTAMP:" + stamp);
            if (e.startTime() == null) {
                // подія на весь день: DTEND ексклюзивний, тому наступна доба
                line(out, "DTSTART;VALUE=DATE:" + DATE.format(e.date()));
                line(out, "DTEND;VALUE=DATE:" + DATE.format(e.date().plusDays(1)));
            } else {
                LocalDateTime start = LocalDateTime.of(e.date(), e.startTime());
                int minutes = e.durationMinutes() > 0 ? e.durationMinutes() : 60;
                line(out, "DTSTART:" + DATE_TIME.format(start));
                line(out, "DTEND:" + DATE_TIME.format(start.plusMinutes(minutes)));
            }
            line(out, "SUMMARY:" + escape(e.summary()));
            if (e.description() != null && !e.description().isBlank()) {
                line(out, "DESCRIPTION:" + escape(e.description()));
            }
            if (e.lat() != null && e.lon() != null) {
                // GEO — машинні координати; LOCATION людиночитний, його показує UI календаря
                line(out, "GEO:" + e.lat() + ";" + e.lon());
                line(out, "LOCATION:" + escape(e.lat() + ", " + e.lon()));
            }
            line(out, "END:VEVENT");
        }

        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** Стабільний UID: той самий пункт при повторному імпорті оновиться, а не задвоїться. */
    public static String uid(Object id) {
        return id + "@waylo";
    }

    /**
     * RFC 5545 §3.1: рядок не довший за 75 ОКТЕТІВ, продовження починається з пробілу.
     * Рахуємо байти в UTF-8, а не символи — інакше кирилиця й діакритика (вдвічі
     * довші в байтах) мовчки виїдуть за ліміт, і частина календарів обріже рядок.
     * Різати можна лише між символами, тому йдемо по code point'ах.
     */
    private static void line(StringBuilder out, String content) {
        int limit = 75;
        int bytes = 0;
        StringBuilder chunk = new StringBuilder();
        boolean first = true;

        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > limit) {
                out.append(chunk).append(CRLF);
                chunk.setLength(0);
                chunk.append(' ');          // пробіл-продовження вже займає октет
                bytes = 1;
                first = false;
                limit = 75;
            }
            chunk.append(ch);
            bytes += size;
            i += Character.charCount(cp);
        }
        if (chunk.length() > 0 || first) {
            out.append(chunk).append(CRLF);
        }
    }

    /** RFC 5545 §3.3.11: зворотний слеш, крапка з комою, кома й переводи рядків. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
