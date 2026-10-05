package com.waylo.place.ownplace;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Витягує {@link OwnPlacePreview} зі звичайного (не short) посилання Google Maps.
 * Підтримує основні формати:
 *   /place/&lt;name&gt;/@LAT,LON,...
 *   /@LAT,LON,...
 *   ?q=LAT,LON
 *   /maps?q=LAT,LON (query з "q")
 *   data=...!3dLAT!4dLON   (внутрішній формат, що зберігає точні координати пина)
 *
 * Для коротких https://maps.app.goo.gl/... спершу треба розгорнути через
 * {@link GoogleMapsLinkResolver}, а потім подати сюди.
 */
@Component
public class GoogleMapsLinkParser {

    // @LAT,LON (десяткові з крапкою; підтримуємо від'ємні)
    private static final Pattern AT_COORD =
        Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    // !3dLAT!4dLON — точне положення пина
    private static final Pattern BANG_COORD =
        Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)");
    // ?q=LAT,LON — старий формат
    private static final Pattern Q_COORD =
        Pattern.compile("[?&]q=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    // /place/<name>/
    private static final Pattern PLACE_NAME =
        Pattern.compile("/place/([^/@]+)");

    public Optional<OwnPlacePreview> parse(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        String decoded;
        try {
            decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            decoded = url;
        }

        Double lat = null, lon = null;
        // Пріоритет: пин з !3d!4d (найточніший) → @coord → q=
        Matcher m = BANG_COORD.matcher(decoded);
        if (m.find()) { lat = Double.parseDouble(m.group(1)); lon = Double.parseDouble(m.group(2)); }
        if (lat == null) {
            m = AT_COORD.matcher(decoded);
            if (m.find()) { lat = Double.parseDouble(m.group(1)); lon = Double.parseDouble(m.group(2)); }
        }
        if (lat == null) {
            m = Q_COORD.matcher(decoded);
            if (m.find()) { lat = Double.parseDouble(m.group(1)); lon = Double.parseDouble(m.group(2)); }
        }
        if (lat == null || lon == null) return Optional.empty();
        if (!isValidLat(lat) || !isValidLon(lon)) return Optional.empty();

        String name = extractName(decoded);
        return Optional.of(new OwnPlacePreview(name, lat, lon, "google-maps", url));
    }

    private String extractName(String decoded) {
        Matcher m = PLACE_NAME.matcher(decoded);
        if (m.find()) {
            String raw = m.group(1).replace('+', ' ').trim();
            if (!raw.isEmpty()) return raw;
        }
        // Фолбек — координати в тексті
        return "Власне місце";
    }

    private boolean isValidLat(double v) { return v >= -90 && v <= 90; }
    private boolean isValidLon(double v) { return v >= -180 && v <= 180; }

    /** Перевірка схожості на URL до Google Maps / Google / goo.gl. */
    public static boolean isGoogleMapsHost(URI uri) {
        if (uri == null || uri.getHost() == null) return false;
        String h = uri.getHost().toLowerCase();
        return h.endsWith("google.com") || h.endsWith("google.com.ua")
            || h.equals("goo.gl") || h.equals("maps.app.goo.gl");
    }
}
