package com.waylo.place.ownplace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Повний цикл: user URL → (розгортання коротких посилань) → парсер → preview.
 * Для безпеки обмежуємо host-ом google-доменів — щоб ендпоінт не став
 * SSRF-шлюзом до внутрішніх сервісів.
 */
@Component
public class GoogleMapsLinkResolver {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsLinkResolver.class);

    private final GoogleMapsLinkParser parser;
    private final HttpClient http;

    public GoogleMapsLinkResolver(GoogleMapsLinkParser parser) {
        this.parser = parser;
        this.http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public Optional<OwnPlacePreview> resolve(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return Optional.empty();
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http")
                                        || uri.getScheme().equalsIgnoreCase("https"))) {
            return Optional.empty();
        }
        if (!GoogleMapsLinkParser.isGoogleMapsHost(uri)) {
            return Optional.empty();
        }
        String effective = rawUrl;

        // Короткі посилання (maps.app.goo.gl, goo.gl) — ходимо, щоб витягти кінцевий URL
        String host = uri.getHost().toLowerCase();
        if (host.equals("maps.app.goo.gl") || host.equals("goo.gl")) {
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "TravelPlanner/1.0")
                    .GET()
                    .build();
                HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
                effective = res.uri().toString();
                log.info("resolved short link: {} → {}", rawUrl, effective);
            } catch (Exception ex) {
                log.warn("не вдалося розгорнути {}: {}", rawUrl, ex.getMessage());
                return Optional.empty();
            }
        }
        return parser.parse(effective);
    }
}
