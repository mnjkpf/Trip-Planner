package com.waylo.trip.service;

import com.waylo.trip.access.TripAccess;
import com.waylo.trip.client.MediaClient;
import com.waylo.trip.client.MediaTicket;
import com.waylo.trip.consumer.MediaReadyEvent;
import com.waylo.trip.domain.ItineraryItem;
import com.waylo.trip.domain.OutboxEvent;
import com.waylo.trip.domain.PhotoStatus;
import com.waylo.trip.domain.TripDay;
import com.waylo.trip.domain.TripPhoto;
import com.waylo.trip.domain.TripRole;
import com.waylo.trip.dto.PhotoResponse;
import com.waylo.trip.dto.PhotoUploadRequest;
import com.waylo.trip.dto.PhotoUploadResponse;
import com.waylo.trip.error.ApiExceptions.TripForbiddenException;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.repository.ItineraryItemRepository;
import com.waylo.trip.repository.OutboxRepository;
import com.waylo.trip.repository.TripDayRepository;
import com.waylo.trip.repository.TripPhotoRepository;
import com.waylo.trip.sse.PhotosChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Фото подорожі. trip-service тримає лише посилання на файл і контекст, а самі
 * байти живуть у media-service — тому тут немає жодного рядка про MinIO.
 *
 * Шлях завантаження навмисно у два кроки:
 *   1) клієнт просить дозвіл → перевіряємо права (EDITOR) і беремо тікет;
 *   2) клієнт шле файл напряму в media-service.
 * Так байти не течуть крізь цей сервіс, а перевірка прав лишається там, де
 * про них узагалі відомо.
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    /** Скільки терпимо «висяк» у статусі UPLOADING, перш ніж прибрати рядок. */
    private static final Duration STALE_AFTER = Duration.ofHours(2);
    /** Запобіжник від випадкового заливання всього фотоальбому в одну подорож. */
    private static final long MAX_PHOTOS_PER_TRIP = 300;

    private final TripPhotoRepository photoRepository;
    private final ItineraryItemRepository itemRepository;
    private final TripDayRepository tripDayRepository;
    private final OutboxRepository outboxRepository;
    private final MediaClient mediaClient;
    private final TripAccess access;
    private final ApplicationEventPublisher events;
    private final JsonMapper jsonMapper;
    private final String deleteTopic;

    public PhotoService(TripPhotoRepository photoRepository,
                        ItineraryItemRepository itemRepository,
                        TripDayRepository tripDayRepository,
                        OutboxRepository outboxRepository,
                        MediaClient mediaClient,
                        TripAccess access,
                        ApplicationEventPublisher events,
                        JsonMapper jsonMapper,
                        @Value("${app.topics.media-delete-requested}") String deleteTopic) {
        this.photoRepository = photoRepository;
        this.itemRepository = itemRepository;
        this.tripDayRepository = tripDayRepository;
        this.outboxRepository = outboxRepository;
        this.mediaClient = mediaClient;
        this.access = access;
        this.events = events;
        this.jsonMapper = jsonMapper;
        this.deleteTopic = deleteTopic;
    }

    /** Крок 1: дозвіл на завантаження + рядок-заготовка у статусі UPLOADING. */
    @Transactional
    public PhotoUploadResponse requestUpload(UUID userId, UUID tripId, PhotoUploadRequest req) {
        access.require(userId, tripId, TripRole.EDITOR);
        if (photoRepository.countByTripId(tripId) >= MAX_PHOTOS_PER_TRIP) {
            throw new TripForbiddenException("У подорожі вже максимум фото (" + MAX_PHOTOS_PER_TRIP + ")");
        }
        ItineraryItem item = req.itemId() == null ? null : requireItem(tripId, req.itemId());
        MediaTicket ticket = mediaClient.requestTicket(tripId, userId);

        TripPhoto photo = TripPhoto.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .mediaId(ticket.mediaId())
                .itineraryItemId(item == null ? null : item.getId())
                .placeName(item == null ? null : item.getPlaceName())
                .caption(trimToNull(req.caption()))
                .status(PhotoStatus.UPLOADING)
                .uploadedBy(userId)
                .build();
        photoRepository.save(photo);

        return new PhotoUploadResponse(photo.getId(), ticket.mediaId(), ticket.ticket(),
                ticket.uploadPath(), ticket.maxBytes(), ticket.contentTypes());
    }

    /**
     * Крок 2 (приходить подією): файл на місці. Ідемпотентно — повторна доставка
     * лише перезапише ті самі розміри. Невідомий mediaId ігноруємо: фото могли
     * видалити, поки воркер робив мініатюру.
     */
    @Transactional
    public void applyReady(MediaReadyEvent event) {
        UUID mediaId = UUID.fromString(event.mediaId());
        TripPhoto photo = photoRepository.findByMediaId(mediaId).orElse(null);
        if (photo == null) {
            log.info("media.ready для невідомого фото {} — прошу media-service прибрати байти", mediaId);
            requestMediaDelete(List.of(mediaId), event.context());
            return;
        }
        photo.setStatus(PhotoStatus.READY);
        photo.setContentType(event.contentType());
        photo.setBytes(event.bytes());
        photo.setWidth(event.width());
        photo.setHeight(event.height());
        photo.setReadyAt(event.readyAt() != null ? event.readyAt() : Instant.now());
        events.publishEvent(new PhotosChanged(photo.getTripId()));
        log.info("фото {} готове у подорожі {}", photo.getId(), photo.getTripId());
    }

    /** Галерея подорожі. Глядач теж бачить усе — ділитись враженнями можуть усі. */
    @Transactional(readOnly = true)
    public List<PhotoResponse> list(UUID userId, UUID tripId) {
        access.require(userId, tripId, TripRole.VIEWER);
        return photoRepository.findByTripIdOrderByCreatedAtAsc(tripId).stream()
                .map(p -> PhotoResponse.of(p, userId))
                .toList();
    }

    /** Видаляти може автор фото або власник подорожі. */
    @Transactional
    public void delete(UUID userId, UUID tripId, UUID photoId) {
        access.require(userId, tripId, TripRole.VIEWER);
        TripPhoto photo = photoRepository.findByIdAndTripId(photoId, tripId)
                .orElseThrow(() -> new TripNotFoundException("Фото не знайдено"));
        boolean mine = photo.getUploadedBy().equals(userId);
        if (!mine && access.roleOf(userId, tripId) != TripRole.OWNER) {
            throw new TripForbiddenException("Видалити фото може той, хто його додав, або власник подорожі");
        }
        photoRepository.delete(photo);
        // Рядок і подія — в одній транзакції: байти не лишаться «сиротами»,
        // навіть якщо media-service саме зараз лежить.
        requestMediaDelete(List.of(photo.getMediaId()), "trip:" + tripId);
        events.publishEvent(new PhotosChanged(tripId));
    }

    /** Фото для публічної сторінки: лише готові й без того, хто їх завантажив. */
    @Transactional(readOnly = true)
    public List<TripPhoto> readyForTrip(UUID tripId) {
        return photoRepository.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, PhotoStatus.READY);
    }

    /**
     * Прибирання «висяків»: тікет видали, а файл так і не приїхав (користувач
     * закрив вкладку чи зникла мережа). Без цього галерея збирала б вічні
     * плейсхолдери.
     */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    @Transactional
    public void cleanupStale() {
        List<TripPhoto> stale = photoRepository.findByStatusAndCreatedAtBefore(
                PhotoStatus.UPLOADING, Instant.now().minus(STALE_AFTER));
        if (stale.isEmpty()) {
            return;
        }
        for (TripPhoto photo : stale) {
            photoRepository.delete(photo);
            // Байти могли все ж доїхати після таймауту — хай media-service перевірить.
            requestMediaDelete(List.of(photo.getMediaId()), "trip:" + photo.getTripId());
        }
        log.info("прибрано недовантажених фото: {}", stale.size());
    }

    // ---- helpers ----

    /** Пункт маршруту має належати саме цій подорожі — інакше фото «приклеїлось» би до чужого дня. */
    private ItineraryItem requireItem(UUID tripId, UUID itemId) {
        ItineraryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new TripNotFoundException("Пункт маршруту не знайдено"));
        TripDay day = tripDayRepository.findById(item.getTripDayId())
                .orElseThrow(() -> new TripNotFoundException("День не знайдено"));
        if (!day.getTripId().equals(tripId)) {
            throw new TripNotFoundException("Пункт не належить цій подорожі");
        }
        return item;
    }

    private void requestMediaDelete(List<UUID> mediaIds, String context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mediaIds", mediaIds.stream().map(UUID::toString).toList());
        payload.put("context", context);
        outboxRepository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Media")
                .aggregateId(mediaIds.get(0))
                .eventType(deleteTopic)
                .payload(toJson(payload))
                .attempts(0)
                .build());
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Не вдалося серіалізувати подію видалення медіа", e);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
