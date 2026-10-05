package com.waylo.trip.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Одна й та сама HTTP-обгортка для .ics у приватному та публічному контролерах —
 * щоб заголовки (тип, кодування, імʼя файлу) не розʼїхались між ними.
 */
public final class IcsResponse {

    private IcsResponse() {}

    public static ResponseEntity<byte[]> attachment(IcsExporter exporter, IcsCalendar calendar) {
        byte[] body = exporter.export(calendar).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(CalendarService.fileName(calendar.name()), StandardCharsets.UTF_8)
                        .build().toString())
                .body(body);
    }
}
