package com.waylo.trip.service;

import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.Trip;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.domain.TripShare;
import com.waylo.trip.dto.ShareLinkResponse;
import com.waylo.trip.dto.SharedTripResponse;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.TripDayRepository;
import com.waylo.trip.repository.TripRepository;
import com.waylo.trip.repository.TripShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Публічні посилання на маршрут: «будь-хто з посиланням бачить, редагувати не може».
 *
 * Чому окремий сервіс, а не ще кілька методів у TripService: тут інша модель
 * доступу. Усюди в TripService вхід — userId із gateway, а тут публічний вхід
 * за токеном БЕЗ користувача взагалі, і дуже важливо не переплутати ці два шляхи.
 * Окремий клас і окремий вузький DTO роблять цю межу видимою.
 */
@Service
public class ShareService {

    /** 32 байти ентропії → 43 символи base64url. Вгадати перебором нереально. */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final TripRepository tripRepository;
    private final TripShareRepository shareRepository;
    private final TripDayRepository tripDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;

    public ShareService(TripRepository tripRepository,
                        TripShareRepository shareRepository,
                        TripDayRepository tripDayRepository,
                        ItineraryItemRepository itineraryItemRepository) {
        this.tripRepository = tripRepository;
        this.shareRepository = shareRepository;
        this.tripDayRepository = tripDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
    }

    /**
     * Створює посилання або повертає вже активне — метод ідемпотентний, тому
     * повторний клік «Поділитися» не плодить токенів і не ламає вже розіслане
     * посилання.
     */
    @Transactional
    public ShareLinkResponse share(UUID userId, UUID tripId) {
        requireOwned(userId, tripId);
        TripShare existing = shareRepository.findByTripIdAndRevokedAtIsNull(tripId).orElse(null);
        if (existing != null) {
            return ShareLinkResponse.of(existing.getToken(), existing.getCreatedAt());
        }
        // saveAndFlush, а не save: треба, щоб INSERT пройшов одразу й @CreationTimestamp
        // проставив createdAt — інакше у відповідь пішов би null.
        TripShare created = shareRepository.saveAndFlush(TripShare.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .token(newToken())
                .createdBy(userId)
                .build());
        return ShareLinkResponse.of(created.getToken(), created.getCreatedAt());
    }

    /** Поточне посилання, якщо воно є; порожньо — якщо подорожжю ще не ділилися. */
    @Transactional(readOnly = true)
    public Optional<ShareLinkResponse> current(UUID userId, UUID tripId) {
        requireOwned(userId, tripId);
        return shareRepository.findByTripIdAndRevokedAtIsNull(tripId)
                .map(s -> ShareLinkResponse.of(s.getToken(), s.getCreatedAt()));
    }

    /**
     * Відкликання. М'яке (revokedAt), щоб лишився слід у БД; нове посилання
     * після цього матиме інший токен, тож старе вже нікому не відкриється.
     */
    @Transactional
    public void revoke(UUID userId, UUID tripId) {
        requireOwned(userId, tripId);
        shareRepository.findByTripIdAndRevokedAtIsNull(tripId)
                .ifPresent(s -> s.setRevokedAt(Instant.now()));
    }

    /**
     * Публічний перегляд — БЕЗ userId: власника тут немає й бути не може.
     * Віддаємо лише маршрут і мінімум про подорож (див. SharedTripResponse).
     */
    @Transactional(readOnly = true)
    public SharedTripResponse viewShared(String token) {
        TripShare share = shareRepository.findByTokenAndRevokedAtIsNull(token)
                .orElseThrow(() -> new TripNotFoundException("Посилання недійсне або відкликане"));
        Trip trip = tripRepository.findById(share.getTripId())
                .orElseThrow(() -> new TripNotFoundException("Посилання недійсне або відкликане"));

        List<SharedTripResponse.Day> days = tripDayRepository
                .findByTripIdOrderByDayIndexAsc(trip.getId()).stream()
                .map(this::toDay)
                .toList();

        return new SharedTripResponse(
                trip.getTitle(),
                trip.getDestinationName(),
                trip.getDestinationCountry(),
                trip.getDestinationLat(),
                trip.getDestinationLon(),
                trip.getStartDate(),
                trip.getEndDate(),
                days);
    }

    private SharedTripResponse.Day toDay(TripDay d) {
        List<ItineraryItem> rows = itineraryItemRepository.findByTripDayIdOrderByOrderIndexAsc(d.getId());
        List<SharedTripResponse.Item> items = new ArrayList<>();
        double meters = 0;
        int walk = 0;
        ItineraryItem prev = null;
        for (ItineraryItem i : rows) {
            if (prev != null) {
                meters += haversine(prev.getPlaceLat(), prev.getPlaceLon(), i.getPlaceLat(), i.getPlaceLon());
            }
            if (i.getTravelMinutesFromPrev() != null) {
                walk += i.getTravelMinutesFromPrev();
            }
            items.add(new SharedTripResponse.Item(
                    i.getOrderIndex(), i.getPlaceName(), i.getPlaceCategory(),
                    i.getPlaceLat(), i.getPlaceLon(),
                    fmtTime(i.getPlannedStart()), i.getDwellMinutes(),
                    i.getTravelMinutesFromPrev(), i.getNote()));
            prev = i;
        }
        double km = Math.round(meters / 100.0) / 10.0;
        return new SharedTripResponse.Day(d.getDayIndex(), d.getDayDate(), km, walk, items);
    }

    private Trip requireOwned(UUID userId, UUID tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripNotFoundException("Подорож не знайдено"));
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private static String fmtTime(LocalTime t) {
        return t == null ? null : String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

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
