package com.waylo.place.provider;

import com.waylo.place.domain.PlaceCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Чистий юніт-тест розбору GeoJSON Geoapify → PlaceCandidate і маппінгу категорій.
 * Без Spring/мережі — лише логіка мапера на канонічній відповіді.
 */
class GeoapifyMapperTest {

    private final GeoapifyMapper mapper = new GeoapifyMapper(JsonMapper.builder().build());

    @Test
    void parsesFeatures_intoCandidates_skippingIncomplete() {
        String json = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{
                     "name":"Colosseum","place_id":"abc123",
                     "categories":["tourism.sights","tourism.attraction"],
                     "lat":41.89,"lon":12.49,"city":"Rome","country_code":"it",
                     "formatted":"Piazza del Colosseo, Rome","website":"https://colosseo.it"}},
                  {"type":"Feature","properties":{
                     "name":"Bar Roma","place_id":"def456","categories":["catering.bar"],
                     "lat":41.90,"lon":12.50,"city":"Rome","country_code":"it"}},
                  {"type":"Feature","properties":{"place_id":"noName","lat":1.0,"lon":2.0}}
                ]}
                """;

        List<PlaceCandidate> cands = mapper.parse(json);

        assertEquals(2, cands.size());   // третій без name — пропущено

        PlaceCandidate colosseum = cands.get(0);
        assertEquals("geoapify", colosseum.provider());
        assertEquals("abc123", colosseum.externalId());
        assertEquals("Colosseum", colosseum.name());
        assertEquals(PlaceCategory.ATTRACTION, colosseum.category());
        assertEquals("IT", colosseum.countryCode());
        assertEquals(41.89, colosseum.lat(), 1e-9);
        assertEquals(12.49, colosseum.lon(), 1e-9);
        assertEquals("Rome", colosseum.city());
        assertTrue(colosseum.rawPayload().contains("Colosseum"));   // сирий payload збережено

        assertEquals(PlaceCategory.BAR, cands.get(1).category());
    }

    @Test
    void toGeoapifyCategories_mapsEnum_andDefault() {
        assertEquals("catering.restaurant", mapper.toGeoapifyCategories(PlaceCategory.RESTAURANT));
        assertEquals("entertainment.museum", mapper.toGeoapifyCategories(PlaceCategory.MUSEUM));
        assertTrue(mapper.toGeoapifyCategories(null).contains("catering"));   // широкий дефолт
    }

    @Test
    void blankOrEmpty_returnsEmpty() {
        assertTrue(mapper.parse("").isEmpty());
        assertTrue(mapper.parse("{\"features\":[]}").isEmpty());
    }
}
