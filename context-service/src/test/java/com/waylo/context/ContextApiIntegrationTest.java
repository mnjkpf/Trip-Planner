package com.waylo.context;

import com.waylo.context.weather.DailyWeather;
import com.waylo.context.weather.WeatherClient;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-контракт /api/context: сезон рахуємо самі (за місяцем/півкулею), погоду
 * бере WeatherClient — тут замокано, тож справжній Open-Meteo не викликаємо.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContextApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    WeatherClient weatherClient;

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void context_returnsSeason_andWeatherDays() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        Mockito.when(weatherClient.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(
                        new DailyWeather(start, 15.0, 27.0, 0.0),
                        new DailyWeather(start.plusDays(1), 16.0, 28.0, 1.5)));

        MvcResult res = mockMvc.perform(get("/api/context")
                        .param("lat", "50.0")
                        .param("lon", "30.0")
                        .param("startDate", start.toString())
                        .param("endDate", start.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = asMap(res.getResponse().getContentAsString());
        assertNotNull(body.get("season"));
        assertNotNull(body.get("climateHint"));
        List<Map<String, Object>> days = (List<Map<String, Object>>) body.get("days");
        assertEquals(2, days.size());
        assertEquals(27.0, days.get(0).get("tempMaxC"));
    }

    /**
     * Дні за горизонтом прогнозу все одно є у відповіді — але порожні.
     * Інакше смужка погоди просто обривалась би на півдорозі.
     */
    @Test
    @SuppressWarnings("unchecked")
    void daysBeyondForecastHorizon_areReturnedEmpty() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        Mockito.when(weatherClient.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(new DailyWeather(start, 10.0, 18.0, 2.0)));

        MvcResult res = mockMvc.perform(get("/api/context")
                        .param("lat", "50.0")
                        .param("lon", "30.0")
                        .param("startDate", start.toString())
                        .param("endDate", start.plusDays(4).toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> days =
                (List<Map<String, Object>>) asMap(res.getResponse().getContentAsString()).get("days");
        assertEquals(5, days.size(), "усі дні подорожі мають бути у відповіді");
        assertEquals(18.0, days.get(0).get("tempMaxC"));
        assertNull(days.get(4).get("tempMaxC"), "на день без прогнозу — порожньо, а не пропуск");
        assertEquals(start.plusDays(4).toString(), days.get(4).get("date"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void southernHemisphere_july_isWinter_andWeatherMayBeEmpty() throws Exception {
        Mockito.when(weatherClient.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of());

        MvcResult res = mockMvc.perform(get("/api/context")
                        .param("lat", "-33.9")
                        .param("lon", "151.2")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-05"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = asMap(res.getResponse().getContentAsString());
        assertEquals("WINTER", body.get("season"));
        // Дати в минулому: прогнозу немає, але самі дні у відповіді лишаються.
        List<Map<String, Object>> days = (List<Map<String, Object>>) body.get("days");
        assertEquals(5, days.size());
        assertTrue(days.stream().allMatch(d -> d.get("tempMaxC") == null));
    }

    @Test
    void missingStartDate_returns400() throws Exception {
        mockMvc.perform(get("/api/context")
                        .param("lat", "50.0")
                        .param("lon", "30.0")
                        .param("endDate", "2026-07-02"))
                .andExpect(status().isBadRequest());
    }
}
