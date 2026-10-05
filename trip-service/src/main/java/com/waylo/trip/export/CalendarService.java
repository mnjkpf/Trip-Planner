package com.waylo.trip.export;

import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.domain.TripShare;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.TripDayRepository;
import com.waylo.trip.repository.TripRepository;
import com.waylo.trip.repository.TripShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Збирає маршрут у календар. Два входи — власник за JWT і гість за токеном
 * посилання — сходяться в одному мапері, тож .ics у них однаковий: гість бачить
 * рівно той самий розклад, що й власник, і ніяких зайвих полів.
 */
@Service
public class CalendarService {

    private final TripRepository tripRepository;
    private final TripShareRepository shareRepository;
    private final TripDayRepository tripDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;

    public CalendarService(TripRepository tripRepository,
                           TripShareRepository shareRepository,
                           TripDayRepository tripDayRepository,
                           ItineraryItemRepository itineraryItemRepository) {
        this.tripRepository = tripRepository;
        this.shareRepository = shareRepository;
        this.tripDayRepository = tripDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
    }

    @Transactional(readOnly = true)
    public IcsCalendar forOwner(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено"));
        return build(trip);
    }

    @Transactional(readOnly = true)
    public IcsCalendar forShare(String token) {
        TripShare share = shareRepository.findByTokenAndRevokedAtIsNull(token)
                .orElseThrow(() -> new TripNotFoundException("Посилання недійсне або відкликане"));
        Trip trip = tripRepository.findById(share.getTripId())
                .orElseThrow(() -> new TripNotFoundException("Посилання недійсне або відкликане"));
        return build(trip);
    }

    private IcsCalendar build(Trip trip) {
        List<IcsCalendar.Event> events = new ArrayList<>();
        for (TripDay day : tripDayRepository.findByTripIdOrderByDayIndexAsc(trip.getId())) {
            for (ItineraryItem item : itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(day.getId())) {
                events.add(new IcsCalendar.Event(
                        IcsExporter.uid(item.getId()),
                        item.getPlaceName(),
                        day.getDayDate(),
                        item.getPlannedStart(),
                        item.getDwellMinutes(),
                        item.getPlaceLat(),
                        item.getPlaceLon(),
                        description(item)));
            }
        }
        return new IcsCalendar(trip.getTitle(), events);
    }

    private static String description(ItineraryItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getPlaceCategory() != null) {
            sb.append(item.getPlaceCategory());
        }
        if (item.getNote() != null && !item.getNote().isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(item.getNote());
        }
        return sb.toString();
    }

    /**
     * Імʼя файлу з назви подорожі: лише ASCII-літери, цифри й дефіси. Браузери
     * і файлові системи по-різному поводяться з юнікодом у Content-Disposition,
     * тож не ризикуємо — діакритику згортаємо, решту викидаємо.
     */
    public static String fileName(String tripTitle) {
        String base = Normalizer.normalize(tripTitle == null ? "" : tripTitle, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (base.isBlank()) {
            base = "trip";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60).replaceAll("-+$", "");
        }
        return base + ".ics";
    }
}
