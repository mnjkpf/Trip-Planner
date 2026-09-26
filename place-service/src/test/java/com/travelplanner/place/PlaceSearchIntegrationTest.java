package com.travelplanner.place;

import com.travelplanner.place.domain.Place;
import com.travelplanner.place.provider.PlaceEnricher;
import com.travelplanner.place.provider.PlacesProvider;
import com.travelplanner.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-контракт place-service через реальний DispatcherServlet (MockMvc), реальний
 * PostGIS (Testcontainers) і справжній просторовий запит ST_DWithin.
 * Дані вставляємо прямо через репозиторій (write-ендпоінта ще нема — провайдер заглушка),
 * тому кожен тест кладе місце в УНІКАЛЬНУ точку й перевіряє результат за конкретним id,
 * що робить тести стійкими до спільної БД між тестами.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceSearchIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    PlaceRepository placeRepository;
    @Autowired
    GeometryFactory geometryFactory;

    // Провайдер замокано: тести працюють лише з тим, що самі кладуть у БД,
    // і не ходять у справжній Geoapify. Незастабований мок повертає порожній список.
    @MockitoBean
    PlacesProvider placesProvider;

    // Збагачувач фото теж замокано — тести не ходять у Wikipedia.
    // Незастабований мок дає isEnabled()=false, тож getById не запускає збагачення.
    @MockitoBean
    PlaceEnricher placeEnricher;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(String json) {
        return jsonMapper.readValue(json, List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private Place savePlace(String name, String category, double lat, double lon) {
        Point loc = geometryFactory.createPoint(new Coordinate(lon, lat)); // X=довгота, Y=широта
        loc.setSRID(4326);
        Place p = Place.builder()
                .id(UUID.randomUUID())
                .name(name)
                .category(category)
                .location(loc)
                .countryCode("UA")
                .city("TestCity")
                .sourceProvider("test")
                .fetchedAt(Instant.now())
                .build();
        return placeRepository.save(p);
    }

    private boolean containsId(List<Map<String, Object>> places, UUID id) {
        return places.stream().anyMatch(p -> id.toString().equals(p.get("id")));
    }

    @Test
    void search_findsNearbyPlace_withinRadius() throws Exception {
        double lat = 10.0 + Math.random() * 40;
        double lon = 10.0 + Math.random() * 40;
        Place saved = savePlace("Nearby Museum", "MUSEUM", lat, lon);

        MvcResult res = mockMvc.perform(get("/api/places/search")
                        .param("lat", String.valueOf(lat))
                        .param("lon", String.valueOf(lon))
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> places = asList(res.getResponse().getContentAsString());
        assertTrue(containsId(places, saved.getId()));
        Map<String, Object> mine = places.stream()
                .filter(p -> saved.getId().toString().equals(p.get("id")))
                .findFirst().orElseThrow();
        assertEquals("Nearby Museum", mine.get("name"));
        assertEquals("MUSEUM", mine.get("category"));
    }

    @Test
    void search_excludesPlace_outsideRadius() throws Exception {
        double lat = -50.0 + Math.random() * 30;   // південна півкуля — окремо від інших тестів
        double lon = -50.0 + Math.random() * 30;
        Place saved = savePlace("Far Cafe", "CAFE", lat, lon);

        // шукаємо за ~2.2 км на північ (0.02° широти) з радіусом 500 м → місце має випасти
        MvcResult res = mockMvc.perform(get("/api/places/search")
                        .param("lat", String.valueOf(lat + 0.02))
                        .param("lon", String.valueOf(lon))
                        .param("radius", "500"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> places = asList(res.getResponse().getContentAsString());
        assertFalse(containsId(places, saved.getId()));
    }

    @Test
    void search_filtersByCategory() throws Exception {
        double lat = 20.0 + Math.random() * 20;
        double lon = 20.0 + Math.random() * 20;
        Place museum = savePlace("Cat Museum", "MUSEUM", lat, lon);
        Place cafe = savePlace("Cat Cafe", "CAFE", lat + 0.0005, lon + 0.0005); // ~70 м поруч

        MvcResult res = mockMvc.perform(get("/api/places/search")
                        .param("lat", String.valueOf(lat))
                        .param("lon", String.valueOf(lon))
                        .param("radius", "1000")
                        .param("category", "MUSEUM"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> places = asList(res.getResponse().getContentAsString());
        assertTrue(containsId(places, museum.getId()));   // музей у результатах
        assertFalse(containsId(places, cafe.getId()));    // кафе відфільтроване категорією
    }

    @Test
    void getById_returnsPlace() throws Exception {
        double lat = 30.0 + Math.random() * 10;
        double lon = 30.0 + Math.random() * 10;
        Place saved = savePlace("Some Park", "PARK", lat, lon);

        MvcResult res = mockMvc.perform(get("/api/places/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = asMap(res.getResponse().getContentAsString());
        assertEquals(saved.getId().toString(), body.get("id"));
        assertEquals("Some Park", body.get("name"));
        assertEquals("PARK", body.get("category"));
        assertEquals("UA", body.get("countryCode"));
    }

    @Test
    void getById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/places/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/places/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_missingLat_returns400() throws Exception {
        mockMvc.perform(get("/api/places/search").param("lon", "30.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_invalidCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .param("lat", "50.0")
                        .param("lon", "30.0")
                        .param("category", "NONSENSE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_radiusTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .param("lat", "50.0")
                        .param("lon", "30.0")
                        .param("radius", "999999"))
                .andExpect(status().isBadRequest());
    }
}
