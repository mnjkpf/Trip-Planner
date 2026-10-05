package com.waylo.place.airport;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Завантажує {@code classpath:data/airports.json} у памʼять один раз при старті.
 * Список невеликий (~750 записів, ~100 КБ) — простіший і швидший за БД.
 * Парсимо через {@code List<Map>}: Jackson 3 поки не завжди коректно мапить
 * records без явного модуля, а Map-формат гарантовано працює.
 */
@Component
public class AirportCatalog {

    private static final Logger log = LoggerFactory.getLogger(AirportCatalog.class);

    private final List<Airport> airports;

    @Autowired
    @SuppressWarnings("unchecked")
    public AirportCatalog(JsonMapper jsonMapper) throws IOException {
        List<Airport> out = new ArrayList<>();
        try (InputStream in = new ClassPathResource("data/airports.json").getInputStream()) {
            List<Map<String, Object>> raw = jsonMapper.readValue(in, List.class);
            for (Map<String, Object> r : raw) {
                Object lat = r.get("lat");
                Object lon = r.get("lon");
                if (!(lat instanceof Number) || !(lon instanceof Number)) continue;
                out.add(new Airport(
                    String.valueOf(r.get("iata")),
                    String.valueOf(r.getOrDefault("name", "")),
                    String.valueOf(r.getOrDefault("city", "")),
                    String.valueOf(r.getOrDefault("country", "")),
                    ((Number) lat).doubleValue(),
                    ((Number) lon).doubleValue()
                ));
            }
        }
        this.airports = List.copyOf(out);
        log.info("airport catalog loaded: {} airports", airports.size());
    }

    /** Прямий конструктор для тестів — обходимо читання ресурсу. */
    AirportCatalog(List<Airport> airports) {
        this.airports = List.copyOf(airports);
    }

    public List<Airport> all() {
        return airports;
    }
}
