package com.waylo.media.service;

import com.waylo.media.config.MediaProperties;
import com.waylo.media.dto.TicketRequest;
import com.waylo.media.dto.TicketResponse;
import com.waylo.media.error.ApiExceptions.FileTooLargeException;
import com.waylo.media.error.ApiExceptions.InvalidTicketException;
import com.waylo.media.error.ApiExceptions.MediaNotFoundException;
import com.waylo.media.error.ApiExceptions.UnsupportedMediaTypeException;
import com.waylo.media.event.MediaUploadedEvent;
import com.waylo.media.storage.MediaKeys;
import com.waylo.media.storage.ObjectStore;
import com.waylo.media.storage.StoredObject;
import com.waylo.media.ticket.TicketStore;
import com.waylo.media.ticket.UploadTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Логіка завантаження й віддачі. Тонка: уся робота з байтами — в ObjectStore. */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final ObjectStore store;
    private final TicketStore tickets;
    private final MediaProperties props;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String uploadedTopic;

    public MediaService(ObjectStore store,
                        TicketStore tickets,
                        MediaProperties props,
                        KafkaTemplate<String, String> kafkaTemplate,
                        JsonMapper jsonMapper,
                        @Value("${app.topics.media-uploaded}") String uploadedTopic) {
        this.store = store;
        this.tickets = tickets;
        this.props = props;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.uploadedTopic = uploadedTopic;
    }

    public TicketResponse issueTicket(TicketRequest req) {
        UUID mediaId = UUID.randomUUID();
        String token = tickets.issue(new UploadTicket(mediaId, req.context(), req.userId(), props.maxBytes()));
        return new TicketResponse(token, mediaId, "/api/media/upload", props.maxBytes(),
                String.join(",", props.contentTypes()), Instant.now().plus(props.ticketTtl()));
    }

    /**
     * Приймає файл за тікетом і кладе оригінал у сховище. Мініатюра — вже у
     * воркері: користувач не має чекати на перемальовування, поки гортає далі.
     */
    public UUID accept(String token, byte[] bytes, String contentType, String originalName) {
        UploadTicket ticket = tickets.find(token)
                .orElseThrow(() -> new InvalidTicketException("Тікет недійсний або протермінований"));
        if (bytes.length == 0) {
            throw new UnsupportedMediaTypeException("Порожній файл");
        }
        if (bytes.length > ticket.maxBytes()) {
            throw new FileTooLargeException("Файл більший за дозволені " + ticket.maxBytes() + " байт");
        }
        String type = resolveType(contentType, originalName);
        if (!props.allows(type)) {
            throw new UnsupportedMediaTypeException("Непідтримуваний тип файлу: " + type);
        }

        store.put(MediaKeys.original(ticket.mediaId()), bytes, type);
        tickets.consume(token);
        publishUploaded(new MediaUploadedEvent(ticket.mediaId().toString(), ticket.context(),
                type, bytes.length, Instant.now()));
        log.info("прийнято файл {} ({} байт, {}) для context={} від {}",
                ticket.mediaId(), bytes.length, type, ticket.context(), ticket.userId());
        return ticket.mediaId();
    }

    /** Оригінал. */
    public StoredObject original(UUID mediaId) {
        return store.get(MediaKeys.original(mediaId))
                .orElseThrow(() -> new MediaNotFoundException("Файл не знайдено: " + mediaId));
    }

    /**
     * Мініатюра, а якщо її ще (або взагалі) немає — оригінал. Порожній Optional
     * у відповіді означає саме фолбек: контролер тоді не ставить «вічний» кеш,
     * бо мініатюра може зʼявитись за секунду.
     */
    public Thumb thumb(UUID mediaId) {
        Optional<StoredObject> thumb = store.get(MediaKeys.thumb(mediaId));
        return thumb.map(o -> new Thumb(o, true))
                .orElseGet(() -> new Thumb(original(mediaId), false));
    }

    public record Thumb(StoredObject object, boolean generated) {}

    private void publishUploaded(MediaUploadedEvent event) {
        kafkaTemplate.send(uploadedTopic, event.mediaId(), jsonMapper.writeValueAsString(event));
    }

    /**
     * Тип файлу. Браузер інколи шле application/octet-stream (буває на Android
     * при виборі з деяких галерей) — тоді дивимось на розширення в імені.
     */
    public static String resolveType(String contentType, String originalName) {
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
        if (!type.isEmpty() && !"application/octet-stream".equals(type)) {
            return type;
        }
        String name = originalName == null ? "" : originalName.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return type.isEmpty() ? "application/octet-stream" : type;
    }
}
