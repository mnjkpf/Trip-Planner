package com.waylo.media.web;

import com.waylo.media.dto.TicketRequest;
import com.waylo.media.dto.TicketResponse;
import com.waylo.media.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Тікети видаються лише сусіднім сервісам. Шлях /internal/** gateway не роутить
 * взагалі, тож ззовні сюди не достукатись — та сама модель довіри, що й з
 * заголовками X-User-*: межа проходить по gateway, а не всередині мережі.
 */
@RestController
@RequestMapping("/internal/media")
public class InternalMediaController {

    private final MediaService mediaService;

    public InternalMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/tickets")
    public TicketResponse ticket(@Valid @RequestBody TicketRequest req) {
        return mediaService.issueTicket(req);
    }
}
