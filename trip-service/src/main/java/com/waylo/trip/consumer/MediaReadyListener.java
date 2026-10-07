package com.waylo.trip.consumer;

import com.waylo.trip.service.PhotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Споживач media.ready — замикає асинхронний шлях фото до галереї. */
@Component
public class MediaReadyListener {

    private static final Logger log = LoggerFactory.getLogger(MediaReadyListener.class);

    private final JsonMapper jsonMapper;
    private final PhotoService photoService;

    public MediaReadyListener(JsonMapper jsonMapper, PhotoService photoService) {
        this.jsonMapper = jsonMapper;
        this.photoService = photoService;
    }

    @KafkaListener(topics = "${app.topics.media-ready}")
    public void onMediaReady(String payload) {
        MediaReadyEvent event = jsonMapper.readValue(payload, MediaReadyEvent.class);
        log.info("отримано media.ready: media={}, context={}", event.mediaId(), event.context());
        photoService.applyReady(event);
    }
}
