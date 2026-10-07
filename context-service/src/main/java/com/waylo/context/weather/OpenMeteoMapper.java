package com.waylo.context.weather;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Розбір відповіді Open-Meteo (колонковий формат: daily.time[], daily.temperature_2m_min[] ...)
 * у список DailyWeather. Через Map — стійко до Jackson 3 і snake_case ключів.
 */
@Component
public class OpenMeteoMapper {

    private final JsonMapper jsonMapper;

    public OpenMeteoMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @SuppressWarnings("unchecked")
    public List<DailyWeather> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Map<String, Object> root = jsonMapper.readValue(json, Map.class);
        if (!(root.get("daily") instanceof Map<?, ?> daily)) {
            return List.of();
        }
        if (!(daily.get("time") instanceof List<?> times)) {
            return List.of();
        }
        List<?> tmin = asList(daily.get("temperature_2m_min"));
        List<?> tmax = asList(daily.get("temperature_2m_max"));
        List<?> prcp = asList(daily.get("precipitation_sum"));

        List<DailyWeather> result = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            LocalDate date = LocalDate.parse(times.get(i).toString());
            result.add(new DailyWeather(date, at(tmin, i), at(tmax, i), at(prcp, i)));
        }
        return result;
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static Double at(List<?> l, int i) {
        if (i >= l.size()) return null;
        return l.get(i) instanceof Number n ? n.doubleValue() : null;
    }
}
