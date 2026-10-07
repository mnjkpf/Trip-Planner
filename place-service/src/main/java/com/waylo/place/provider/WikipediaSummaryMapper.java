package com.waylo.place.provider;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Розбір відповіді Wikipedia REST Summary API (/page/summary/{title}) → фото місця.
 * Беремо головне зображення статті (thumbnail, з піднятою шириною; інакше originalimage)
 * і wikibase_item (Wikidata Q-id) як бонус. Сторінки-неоднозначності (type=disambiguation)
 * пропускаємо — з них надійного фото не буде.
 */
@Component
public class WikipediaSummaryMapper {

    private final JsonMapper jsonMapper;

    public WikipediaSummaryMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @SuppressWarnings("unchecked")
    public PlaceImage parseSummaryImage(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> root = jsonMapper.readValue(json, Map.class);
        if ("disambiguation".equals(asString(root.get("type")))) {
            return null;
        }
        String wikidata = asString(root.get("wikibase_item"));
        String image = imageFrom(root);
        if (image == null && wikidata == null) {
            return null;
        }
        return new PlaceImage(image, wikidata);
    }

    /**
     * Заголовок першого результату пошуку MediaWiki
     * (/w/api.php?action=query&list=search) або null.
     */
    @SuppressWarnings("unchecked")
    public String parseFirstSearchTitle(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> root = jsonMapper.readValue(json, Map.class);
        if (!(root.get("query") instanceof Map<?, ?> query)) {
            return null;
        }
        if (!(query.get("search") instanceof java.util.List<?> results) || results.isEmpty()) {
            return null;
        }
        if (!(results.get(0) instanceof Map<?, ?> first)) {
            return null;
        }
        return asString(first.get("title"));
    }

    private String imageFrom(Map<String, Object> root) {
        if (root.get("thumbnail") instanceof Map<?, ?> thumb) {
            String src = asString(thumb.get("source"));
            if (src != null && !src.isBlank()) {
                return bumpWidth(src);
            }
        }
        if (root.get("originalimage") instanceof Map<?, ?> orig) {
            String src = asString(orig.get("source"));
            if (src != null && !src.isBlank()) {
                return src;
            }
        }
        return null;
    }

    /** Wikimedia thumbnail .../320px-File.jpg -> .../800px-File.jpg (рендериться на льоту). */
    private String bumpWidth(String thumbUrl) {
        return thumbUrl.replaceFirst("/\\d+px-", "/800px-");
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
