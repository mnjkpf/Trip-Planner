package com.waylo.place.ownplace;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/places/resolve-link
 * body: {"url": "https://maps.app.goo.gl/XYZ"} або повний Google-Maps URL.
 * 200 → {name, lat, lon, source, originalUrl}
 * 400 → не схоже на Google-Maps URL або не змогли витягти координати
 */
@RestController
@RequestMapping("/api/places")
@Validated
public class OwnPlaceController {

    private final GoogleMapsLinkResolver resolver;

    public OwnPlaceController(GoogleMapsLinkResolver resolver) {
        this.resolver = resolver;
    }

    public record ResolveLinkRequest(@NotBlank @Size(max = 2000) String url) {}

    @PostMapping("/resolve-link")
    public ResponseEntity<?> resolve(@Valid @RequestBody ResolveLinkRequest req) {
        return resolver.resolve(req.url())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                "error", "cannot_resolve",
                "message", "Не вдалося витягти координати. Переконайся, що посилання з Google Maps і містить точку."
            )));
    }
}
