package com.waylo.media.process;

import com.waylo.media.event.MediaDeleteRequestedEvent;
import com.waylo.media.storage.MediaKeys;
import com.waylo.media.storage.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/**
 * Видалення байтів на вимогу власника. Через подію, а не прямий виклик: рядок у
 * БД і подія в outbox пишуться однією транзакцією, тож фото не може лишитись
 * «живим» через те, що виклик не дійшов.
 */
@Component
public class MediaDeleteListener {

    private static final Logger log = LoggerFactory.getLogger(MediaDeleteListener.class);

    private final ObjectStore store;
    private final JsonMapper jsonMapper;

    public MediaDeleteListener(ObjectStore store, JsonMapper jsonMapper) {
        this.store = store;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${app.topics.media-delete-requested}")
    public void onDeleteRequested(String payload) {
        MediaDeleteRequestedEvent event = jsonMapper.readValue(payload, MediaDeleteRequestedEvent.class);
        List<String> ids = event.mediaIds() == null ? List.of() : event.mediaIds();
        for (String id : ids) {
            UUID mediaId = UUID.fromString(id);
            // Видалення неіснуючого ключа в S3 — не помилка, тож повтор події безпечний.
            store.delete(MediaKeys.original(mediaId));
            store.delete(MediaKeys.thumb(mediaId));
        }
        log.info("видалено медіа: {} (context={})", ids.size(), event.context());
    }
}
