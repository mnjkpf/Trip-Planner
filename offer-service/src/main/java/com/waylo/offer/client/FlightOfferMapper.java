package com.waylo.offer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.waylo.offer.dto.FlightOffer;
import com.waylo.offer.dto.FlightSearchResponse;
import com.waylo.offer.dto.FlightSegment;

import tools.jackson.databind.json.JsonMapper;

/**
 * Розбір JSON google_flights SerpAPI → наш {@link FlightSearchResponse}.
 * Беремо тільки те, що показуємо у UI; все інше ігноруємо.
 *
 * Формат SerpAPI (скорочено):
 * {
 *   "best_flights":   [{...}],
 *   "other_flights":  [{...}]
 * }
 * один «variation»:
 * {
 *   "flights":[{departure_airport:{id,name,time},arrival_airport:{...},
 *               duration, airline, airline_logo, flight_number, travel_class, airplane}],
 *   "layovers":[{duration,id}], "total_duration":123,
 *   "price":200, "type":"Round trip", "booking_token":"...",
 *   "carbon_emissions":{this_flight: 123000}
 * }
 */
@Component
public class FlightOfferMapper {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FlightOfferMapper.class);

    private final JsonMapper jsonMapper;

    public FlightOfferMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @SuppressWarnings("unchecked")
    public FlightSearchResponse parse(String json, String currency) {
        if (json == null || json.isBlank()) {
            return new FlightSearchResponse(List.of(), List.of());
        }
        Map<String, Object> root = jsonMapper.readValue(json, Map.class);
        Object err = root.get("error");
        if (err != null) {
            log.warn("SerpAPI повернув error: {}", err);
            return new FlightSearchResponse(List.of(), List.of());
        }
        List<FlightOffer> best = mapList((List<Map<String, Object>>) root.get("best_flights"), currency);
        List<FlightOffer> other = mapList((List<Map<String, Object>>) root.get("other_flights"), currency);
        if (best.isEmpty() && other.isEmpty()) {
            String preview = json.length() > 500 ? json.substring(0, 500) + "..." : json;
            log.warn("best_flights/other_flights порожні. Ключі: {}. Preview: {}", root.keySet(), preview);
        }
        return new FlightSearchResponse(best, other);
    }

    @SuppressWarnings("unchecked")
    private List<FlightOffer> mapList(List<Map<String, Object>> raw, String currency) {
        if (raw == null) return List.of();
        List<FlightOffer> out = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) {
            List<Map<String, Object>> segs = (List<Map<String, Object>>) row.get("flights");
            if (segs == null || segs.isEmpty()) continue;
            List<FlightSegment> segments = new ArrayList<>(segs.size());
            for (Map<String, Object> s : segs) {
                Map<String, Object> dep = (Map<String, Object>) s.getOrDefault("departure_airport", Map.of());
                Map<String, Object> arr = (Map<String, Object>) s.getOrDefault("arrival_airport", Map.of());
                segments.add(new FlightSegment(
                    str(dep.get("id")),
                    str(dep.get("name")),
                    str(dep.get("time")),
                    str(arr.get("id")),
                    str(arr.get("name")),
                    str(arr.get("time")),
                    intOf(s.get("duration")),
                    str(s.get("airline")),
                    str(s.get("airline_logo")),
                    str(s.get("flight_number")),
                    str(s.get("travel_class")),
                    str(s.get("airplane"))
                ));
            }
            List<Map<String, Object>> layovers = (List<Map<String, Object>>) row.get("layovers");
            int layoverCount = layovers == null ? 0 : layovers.size();
            Map<String, Object> carbon = (Map<String, Object>) row.get("carbon_emissions");
            Integer carbonGrams = carbon == null ? null : toIntBoxed(carbon.get("this_flight"));
            out.add(new FlightOffer(
                segments,
                intOf(row.get("total_duration")),
                layoverCount,
                dbl(row.get("price")),
                currency,
                str(row.get("type")),
                str(row.get("booking_token")),
                carbonGrams
            ));
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static int intOf(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return (int) Double.parseDouble(s); } catch (NumberFormatException ignore) {}
        }
        return 0;
    }

    private static Integer toIntBoxed(Object v) {
        if (v == null) return null;
        return intOf(v);
    }

    private static double dbl(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignore) {}
        }
        return 0.0;
    }
}
