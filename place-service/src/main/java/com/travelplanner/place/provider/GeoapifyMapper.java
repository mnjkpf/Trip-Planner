package com.travelplanner.place.provider;

import com.travelplanner.place.domain.PlaceCategory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Маппінг між нашою моделлю й Geoapify Places API (GeoJSON).
 * Розбір робимо через Map (без анотацій/JsonNode) — стійко до дрібниць Jackson 3
 * і до snake_case ключів Geoapify (place_id, country_code).
 */
@Component
public class GeoapifyMapper {

    private final JsonMapper jsonMapper;

    public GeoapifyMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Наша категорія → значення параметра categories для запиту в Geoapify. */
    public String toGeoapifyCategories(PlaceCategory category) {
        if (category == null) {
            return "tourism.sights,catering,entertainment,leisure";
        }
        return switch (category) {
            case HOTEL -> "accommodation.hotel";
            case RESTAURANT -> "catering.restaurant";
            case CAFE -> "catering.cafe";
            case BAR -> "catering.bar,catering.pub";
            case MUSEUM -> "entertainment.museum";
            case ATTRACTION -> "tourism.sights,tourism.attraction";
            case PARK -> "leisure.park,national_park";
            case BEACH -> "beach";
            case SHOP -> "commercial";
            case OTHER -> "tourism.sights";
        };
    }

    /** GeoJSON-відповідь Geoapify → нормалізовані кандидати. */
    @SuppressWarnings("unchecked")
    public List<PlaceCandidate> parse(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            return List.of();
        }
        Map<String, Object> root = jsonMapper.readValue(geoJson, Map.class);
        if (!(root.get("features") instanceof List<?> features)) {
            return List.of();
        }

        List<PlaceCandidate> result = new ArrayList<>();
        for (Object f : features) {
            if (!(f instanceof Map<?, ?> feature)) continue;
            if (!(feature.get("properties") instanceof Map<?, ?> props)) continue;

            String name = asString(props.get("name"));
            String externalId = asString(props.get("place_id"));
            Double lat = asDouble(props.get("lat"));
            Double lon = asDouble(props.get("lon"));
            if (name == null || externalId == null || lat == null || lon == null) {
                continue;   // без ключових полів кандидат марний
            }

            result.add(new PlaceCandidate(
                    "geoapify",
                    externalId,
                    name,
                    categoryFrom(props.get("categories")),
                    lat,
                    lon,
                    asString(props.get("city")),
                    upper(asString(props.get("country_code"))),
                    asString(props.get("formatted")),
                    asString(props.get("website")),
                    null,
                    jsonMapper.writeValueAsString(feature)));
        }
        return result;
    }

    private PlaceCategory categoryFrom(Object categoriesObj) {
        if (!(categoriesObj instanceof List<?> cats)) {
            return PlaceCategory.OTHER;
        }
        for (Object o : cats) {
            String c = asString(o);
            if (c == null) continue;
            if (c.startsWith("accommodation")) return PlaceCategory.HOTEL;
            if (c.startsWith("catering.restaurant")) return PlaceCategory.RESTAURANT;
            if (c.startsWith("catering.cafe")) return PlaceCategory.CAFE;
            if (c.startsWith("catering.bar") || c.startsWith("catering.pub")) return PlaceCategory.BAR;
            if (c.startsWith("entertainment.museum")) return PlaceCategory.MUSEUM;
            if (c.startsWith("tourism")) return PlaceCategory.ATTRACTION;
            if (c.startsWith("leisure.park") || c.startsWith("national_park")) return PlaceCategory.PARK;
            if (c.startsWith("beach")) return PlaceCategory.BEACH;
            if (c.startsWith("commercial")) return PlaceCategory.SHOP;
        }
        return PlaceCategory.OTHER;
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
