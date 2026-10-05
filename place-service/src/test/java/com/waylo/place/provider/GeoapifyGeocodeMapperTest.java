package com.waylo.place.provider;

import com.waylo.place.dto.CitySuggestion;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Розбір автодоповнення міст — обидва формати відповіді Geoapify. */
class GeoapifyGeocodeMapperTest {

    private final GeoapifyGeocodeMapper mapper = new GeoapifyGeocodeMapper(JsonMapper.builder().build());

    @Test
    void parsesJsonFormat_results() {
        String json = """
                {"results":[
                  {"city":"Rome","country":"Italy","country_code":"it",
                   "formatted":"Rome, Italy","lat":41.8933203,"lon":12.4829321},
                  {"city":"Rome","country":"United States","country_code":"us",
                   "formatted":"Rome, GA, United States","lat":34.257,"lon":-85.164}
                ]}
                """;
        List<CitySuggestion> cities = mapper.parseCities(json);
        assertEquals(2, cities.size());
        assertEquals("Rome", cities.get(0).name());
        assertEquals("Italy", cities.get(0).country());
        assertEquals("IT", cities.get(0).countryCode());
        assertEquals("Rome, Italy", cities.get(0).formatted());
        assertEquals(41.8933203, cities.get(0).lat(), 1e-9);
        assertEquals(12.4829321, cities.get(0).lon(), 1e-9);
    }

    @Test
    void parsesGeoJsonFormat_features() {
        String json = """
                {"features":[
                  {"properties":{"city":"Paris","country":"France","country_code":"fr",
                   "formatted":"Paris, France","lat":48.8566,"lon":2.3522}}
                ]}
                """;
        List<CitySuggestion> cities = mapper.parseCities(json);
        assertEquals(1, cities.size());
        assertEquals("Paris", cities.get(0).name());
        assertEquals("FR", cities.get(0).countryCode());
    }

    @Test
    void skipsRowsWithoutCoords_andHandlesBlank() {
        assertTrue(mapper.parseCities("").isEmpty());
        assertTrue(mapper.parseCities("{}").isEmpty());
        assertTrue(mapper.parseCities("{\"results\":[{\"city\":\"X\"}]}").isEmpty());
    }
}
