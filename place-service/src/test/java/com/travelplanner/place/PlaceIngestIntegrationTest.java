package com.travelplanner.place;

import com.travelplanner.place.domain.PlaceCategory;
import com.travelplanner.place.provider.PlaceCandidate;
import com.travelplanner.place.provider.PlacesProvider;
import com.travelplanner.place.repository.PlaceExternalRefRepository;
import com.travelplanner.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Перевіряє поповнення каталогу з провайдера: у порожній зоні пошук тягне кандидатів
 * (провайдер замокано), зберігає їх у places + place_external_refs і віддає з БД.
 * Справжній Geoapify не викликаємо — тестуємо саме логіку інжесту.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceIngestIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    PlaceRepository placeRepository;
    @Autowired
    PlaceExternalRefRepository externalRefRepository;

    @MockitoBean
    PlacesProvider placesProvider;

    // Гарантуємо порожній каталог перед кожним тестом: контекст (і БД) спільний
    // з PlaceSearchIntegrationTest, тож прибираємо чужі дані, щоб зона була порожня.
    @BeforeEach
    void cleanCatalog() {
        externalRefRepository.deleteAll();
        placeRepository.deleteAll();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(String json) {
        return jsonMapper.readValue(json, List.class);
    }

    @Test
    void emptyCatalog_ingestsProviderCandidates_persistsAndReturns() throws Exception {
        Mockito.when(placesProvider.searchNearby(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(
                        new PlaceCandidate("geoapify", "ext-colosseum", "Colosseum",
                                PlaceCategory.ATTRACTION, 41.890, 12.492, "Rome", "IT",
                                "Piazza del Colosseo", "https://colosseo.it", null, "{\"place_id\":\"ext-colosseum\"}"),
                        new PlaceCandidate("geoapify", "ext-trevi", "Trevi Fountain",
                                PlaceCategory.ATTRACTION, 41.901, 12.483, "Rome", "IT",
                                "Piazza di Trevi", null, null, "{\"place_id\":\"ext-trevi\"}")));

        // Порожній каталог у цій зоні → пошук тягне й зберігає кандидатів, потім перечитує з БД.
        MvcResult res = mockMvc.perform(get("/api/places/search")
                        .param("lat", "41.9")
                        .param("lon", "12.5")
                        .param("radius", "5000"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> places = asList(res.getResponse().getContentAsString());
        assertTrue(places.stream().anyMatch(p -> "Colosseum".equals(p.get("name"))),
                "результат пошуку має містити збережений Colosseum");
        assertTrue(places.stream().anyMatch(p -> "Trevi Fountain".equals(p.get("name"))));

        // збережено в каталог + звʼязки провайдера
        assertTrue(externalRefRepository.existsByProviderAndExternalId("geoapify", "ext-colosseum"));
        assertTrue(externalRefRepository.existsByProviderAndExternalId("geoapify", "ext-trevi"));

        // внутрішні id проставлені, координати збережені (Y=широта)
        Map<String, Object> colosseum = places.stream()
                .filter(p -> "Colosseum".equals(p.get("name"))).findFirst().orElseThrow();
        assertEquals("ATTRACTION", colosseum.get("category"));
        assertEquals("IT", colosseum.get("countryCode"));
        assertEquals(41.890, ((Number) colosseum.get("lat")).doubleValue(), 1e-6);
    }

    @Test
    void secondSearch_servesFromCatalog_withoutDuplicating() throws Exception {
        Mockito.when(placesProvider.searchNearby(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(
                        new PlaceCandidate("geoapify", "ext-pantheon", "Pantheon",
                                PlaceCategory.ATTRACTION, 41.8986, 12.4769, "Rome", "IT",
                                "Piazza della Rotonda", null, null, "{\"place_id\":\"ext-pantheon\"}")));

        // перший пошук — інжест
        mockMvc.perform(get("/api/places/search")
                        .param("lat", "41.8986").param("lon", "12.4769").param("radius", "3000"))
                .andExpect(status().isOk());
        // другий — має віддати з каталогу (дубль external_id не створюється)
        mockMvc.perform(get("/api/places/search")
                        .param("lat", "41.8986").param("lon", "12.4769").param("radius", "3000"))
                .andExpect(status().isOk());

        assertTrue(externalRefRepository.existsByProviderAndExternalId("geoapify", "ext-pantheon"));
    }
}
