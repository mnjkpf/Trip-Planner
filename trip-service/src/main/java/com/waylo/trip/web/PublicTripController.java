package com.waylo.trip.web;

import com.waylo.trip.dto.SharedTripResponse;
import com.waylo.trip.export.CalendarService;
import com.waylo.trip.export.IcsExporter;
import com.waylo.trip.export.IcsResponse;
import com.waylo.trip.service.ShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Єдиний публічний вхід у trip-service: перегляд маршруту за токеном посилання.
 * Навмисно НЕ під /api/trips/** — gateway пускає сюди без JWT, тож межа між
 * «тільки для власника» і «для будь-кого з посиланням» видно прямо в шляху.
 * Жодного X-User-Id тут не читаємо: його просто не буде.
 */
@RestController
@RequestMapping("/api/public/trips")
public class PublicTripController {

    private final ShareService shareService;
    private final CalendarService calendarService;
    private final IcsExporter icsExporter;

    public PublicTripController(ShareService shareService,
                                CalendarService calendarService,
                                IcsExporter icsExporter) {
        this.shareService = shareService;
        this.calendarService = calendarService;
        this.icsExporter = icsExporter;
    }

    @GetMapping("/{token}")
    public SharedTripResponse view(@PathVariable String token) {
        return shareService.viewShared(token);
    }

    /** Той самий календар, що й у власника — гість теж може покласти маршрут собі. */
    @GetMapping("/{token}/calendar.ics")
    public ResponseEntity<byte[]> calendar(@PathVariable String token) {
        return IcsResponse.attachment(icsExporter, calendarService.forShare(token));
    }
}
