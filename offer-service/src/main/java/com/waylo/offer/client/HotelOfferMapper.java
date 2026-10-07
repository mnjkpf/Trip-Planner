package com.waylo.offer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.waylo.offer.dto.HotelOffer;
import com.waylo.offer.dto.HotelSearchResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Розбір JSON google_hotels SerpAPI → {@link HotelSearchResponse}.
 *
 * SerpAPI повертає масив {@code properties}:
 * {
 *   "name":"...", "type":"hotel", "link":"...",
 *   "gps_coordinates":{"latitude":...,"longitude":...},
 *   "property_token":"...",
 *   "check_in_time":"...", "check_out_time":"...",
 *   "overall_rating":4.5, "reviews":123, "hotel_class":4,
 *   "amenities":["..."],
 *   "images":[{"thumbnail":"...","original_image":"..."}, ...],
 *   "rate_per_night":{"extracted_lowest":123,"lowest":"€123"},
 *   "total_rate":{"extracted_lowest":123,"lowest":"€123"},
 *   "description":"..."
 * }
 */
@Component
public class HotelOfferMapper {

    private final JsonMapper jsonMapper;

    public HotelOfferMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @SuppressWarnings("unchecked")
    public HotelSearchResponse parse(String json, String currency) {
        if (json == null || json.isBlank()) {
            return new HotelSearchResponse(List.of());
        }
        Map<String, Object> root = jsonMapper.readValue(json, Map.class);
        List<Map<String, Object>> props = (List<Map<String, Object>>) root.get("properties");
        if (props == null) return new HotelSearchResponse(List.of());
        List<HotelOffer> out = new ArrayList<>(props.size());
        for (Map<String, Object> p : props) {
            Map<String, Object> gps = (Map<String, Object>) p.get("gps_coordinates");
            Double lat = gps == null ? null : asDouble(gps.get("latitude"));
            Double lon = gps == null ? null : asDouble(gps.get("longitude"));
            Map<String, Object> rpn = (Map<String, Object>) p.get("rate_per_night");
            Map<String, Object> tot = (Map<String, Object>) p.get("total_rate");
            List<String> images = extractImages((List<Map<String, Object>>) p.get("images"));
            List<String> amenities = (List<String>) p.getOrDefault("amenities", List.of());
            out.add(new HotelOffer(
                str(p.get("property_token")),
                str(p.get("name")),
                str(p.get("type")),
                str(p.get("link")),
                lat, lon,
                asDouble(p.get("overall_rating")),
                asInt(p.get("reviews")),
                asInt(p.get("hotel_class")),
                amenities,
                images,
                tot == null ? null : asDouble(tot.get("extracted_lowest")),
                rpn == null ? null : asDouble(rpn.get("extracted_lowest")),
                currency,
                str(p.get("check_in_time")),
                str(p.get("check_out_time")),
                str(p.get("description"))
            ));
        }
        return new HotelSearchResponse(out);
    }

    private static List<String> extractImages(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        List<String> urls = new ArrayList<>(raw.size());
        for (Map<String, Object> img : raw) {
            Object u = img.getOrDefault("original_image", img.get("thumbnail"));
            if (u != null) urls.add(u.toString());
        }
        return urls;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignore) {}
        }
        return null;
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return (int) Double.parseDouble(s); } catch (NumberFormatException ignore) {}
        }
        return null;
    }
}
