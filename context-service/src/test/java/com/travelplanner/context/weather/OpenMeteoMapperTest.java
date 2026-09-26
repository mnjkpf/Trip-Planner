package com.travelplanner.context.weather;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Чистий юніт-тест розбору колонкової відповіді Open-Meteo. */
class OpenMeteoMapperTest {

    private final OpenMeteoMapper mapper = new OpenMeteoMapper(JsonMapper.builder().build());

    @Test
    void parsesDailyColumns_intoRows() {
        String json = """
                {"daily":{
                  "time":["2026-10-01","2026-10-02"],
                  "temperature_2m_min":[10.5,11.0],
                  "temperature_2m_max":[20.1,19.4],
                  "precipitation_sum":[0.0,3.2]}}
                """;

        List<DailyWeather> days = mapper.parse(json);

        assertEquals(2, days.size());
        assertEquals(LocalDate.of(2026, 10, 1), days.get(0).date());
        assertEquals(10.5, days.get(0).tempMinC(), 1e-9);
        assertEquals(20.1, days.get(0).tempMaxC(), 1e-9);
        assertEquals(3.2, days.get(1).precipitationMm(), 1e-9);
    }

    @Test
    void blankOrNoDaily_returnsEmpty() {
        assertTrue(mapper.parse("").isEmpty());
        assertTrue(mapper.parse("{}").isEmpty());
    }
}
