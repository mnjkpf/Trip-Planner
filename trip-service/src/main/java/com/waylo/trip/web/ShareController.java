package com.waylo.trip.web;

import com.waylo.trip.dto.ShareLinkResponse;
import com.waylo.trip.error.ApiExceptions.TripNotFoundException;
import com.waylo.trip.service.ShareService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Керування публічним посиланням — тільки власник подорожі (X-User-Id від gateway). */
@RestController
@RequestMapping("/api/trips/{id}/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /** Ідемпотентно: уже активне посилання повертається, нове не створюється. */
    @PostMapping
    public ShareLinkResponse share(@RequestHeader("X-User-Id") UUID userId,
                                   @PathVariable UUID id) {
        return shareService.share(userId, id);
    }

    @GetMapping
    public ShareLinkResponse current(@RequestHeader("X-User-Id") UUID userId,
                                     @PathVariable UUID id) {
        return shareService.current(userId, id)
                .orElseThrow(() -> new TripNotFoundException("Посилання не створено"));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestHeader("X-User-Id") UUID userId,
                       @PathVariable UUID id) {
        shareService.revoke(userId, id);
    }
}
