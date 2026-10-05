package com.waylo.place.provider;

import com.waylo.place.dto.CitySuggestion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Розбір відповіді Geoapify Geocoding Autocomplete. Навмисно толерантний до формату:
 * розуміє і {"results":[...]} (format=json), і GeoJSON {"features":[{"properties":{...}}]}.
 */
@Component
public class GeoapifyGeocodeMapper {

    private final JsonMapper jsonMapper;

    public GeoapifyGeocodeMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @SuppressWarnings("unchecked")
    public List<CitySuggestion> parseCities(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Map<String, Object> root = jsonMapper.readValue(json, Map.class);

        List<?> rows;
        if (root.get("results") instanceof List<?> r) {
            rows = r;
        } else if (root.get("features") instanceof List<?> f) {
            rows = f;
        } else {
            return List.of();
        }

        List<CitySuggestion> out = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)) continue;
            Map<?, ?> props = m.get("properties") instanceof Map<?, ?> p ? p : m;

            String name = firstNonBlank(
                    asString(props.get("city")),
                    asString(props.get("name")),
                    asString(props.get("address_line1")));
            Double lat = asDouble(props.get("lat"));
            Double lon = asDouble(props.get("lon"));
            if (name == null || lat == null || lon == null) {
                continue;
            }
            out.add(new CitySuggestion(
                    name,
                    asString(props.get("country")),
                    upper(asString(props.get("country_code"))),
                    firstNonBlank(asString(props.get("formatted")), name),
                    lat,
                    lon));
        }
        return out;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase();
    }
}
