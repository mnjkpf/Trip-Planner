package com.waylo.place.provider;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Юніт-тест розбору Wikipedia REST Summary → PlaceImage. Без Spring/мережі.
 */
class WikipediaSummaryMapperTest {

    private final WikipediaSummaryMapper mapper = new WikipediaSummaryMapper(JsonMapper.builder().build());

    @Test
    void picksThumbnail_bumpsWidthTo800_andReadsWikidata() {
        String json = """
                {"type":"standard","title":"Trevi Fountain","wikibase_item":"Q185382",
                 "thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/b/Trevi.jpg/320px-Trevi.jpg","width":320,"height":240},
                 "originalimage":{"source":"https://upload.wikimedia.org/wikipedia/commons/a/b/Trevi.jpg"}}
                """;
        PlaceImage img = mapper.parseSummaryImage(json);
        assertNotNull(img);
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/b/Trevi.jpg/800px-Trevi.jpg", img.imageUrl());
        assertEquals("Q185382", img.wikidataId());
    }

    @Test
    void fallsBackToOriginal_whenNoThumbnail() {
        String json = """
                {"type":"standard","title":"X","originalimage":{"source":"https://upload.wikimedia.org/x.jpg"}}
                """;
        PlaceImage img = mapper.parseSummaryImage(json);
        assertNotNull(img);
        assertEquals("https://upload.wikimedia.org/x.jpg", img.imageUrl());
    }

    @Test
    void disambiguation_returnsNull() {
        assertNull(mapper.parseSummaryImage("{\"type\":\"disambiguation\",\"title\":\"Pantheon\"}"));
    }

    @Test
    void noImageNoWikidata_returnsNull() {
        assertNull(mapper.parseSummaryImage("{\"type\":\"standard\",\"title\":\"X\"}"));
        assertNull(mapper.parseSummaryImage(""));
    }
}
