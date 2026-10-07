package com.waylo.media.process;

import com.waylo.media.config.MediaProperties;
import com.waylo.media.event.MediaReadyEvent;
import com.waylo.media.event.MediaUploadedEvent;
import com.waylo.media.storage.MediaKeys;
import com.waylo.media.storage.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Воркер мініатюр: media.uploaded → зменшена копія → media.ready.
 *
 * Навіщо через Kafka, а не прямо в HTTP-запиті: зменшення великого фото займає
 * сотні мілісекунд і тримало б потік запиту; до того ж тут це природний
 * повтор — якщо впало, подія прийде ще раз. Операція ідемпотентна: мініатюра
 * просто перезаписується тим самим ключем.
 */
@Component
public class ThumbnailWorker {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailWorker.class);

    private final ObjectStore store;
    private final Thumbnailer thumbnailer;
    private final MediaProperties props;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String readyTopic;

    public ThumbnailWorker(ObjectStore store,
                           Thumbnailer thumbnailer,
                           MediaProperties props,
                           KafkaTemplate<String, String> kafkaTemplate,
                           JsonMapper jsonMapper,
                           @Value("${app.topics.media-ready}") String readyTopic) {
        this.store = store;
        this.thumbnailer = thumbnailer;
        this.props = props;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.readyTopic = readyTopic;
    }

    @KafkaListener(topics = "${app.topics.media-uploaded}")
    public void onUploaded(String payload) {
        MediaUploadedEvent event = jsonMapper.readValue(payload, MediaUploadedEvent.class);
        UUID mediaId = UUID.fromString(event.mediaId());

        byte[] original = store.readAll(MediaKeys.original(mediaId));
        Optional<Thumbnailer.Result> thumb =
                thumbnailer.thumbnail(original, props.thumbMaxPx(), props.thumbQuality());

        thumb.ifPresent(r -> store.put(MediaKeys.thumb(mediaId), r.bytes(), "image/jpeg"));
        if (thumb.isEmpty()) {
            log.info("мініатюри для {} не буде ({}) — показуватиметься оригінал", mediaId, event.contentType());
        }

        // Подію шлемо в будь-якому разі: власник має дізнатись, що файл на місці,
        // навіть якщо формат не піддався зменшенню.
        MediaReadyEvent ready = new MediaReadyEvent(
                event.mediaId(), event.context(), event.contentType(), event.bytes(),
                thumb.map(Thumbnailer.Result::sourceWidth).orElse(null),
                thumb.map(Thumbnailer.Result::sourceHeight).orElse(null),
                thumb.isPresent(), Instant.now());
        kafkaTemplate.send(readyTopic, event.mediaId(), jsonMapper.writeValueAsString(ready));
        log.info("медіа {} готове (мініатюра: {})", mediaId, thumb.isPresent());
    }
}
