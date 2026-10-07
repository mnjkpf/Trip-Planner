package com.waylo.place.airport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit-тести на логіку scoring і nearest. Каталог підмінюємо фейковим —
 * без читання JSON з ресурсу й без Spring-контексту.
 */
class AirportServiceTest {

    private AirportService service(List<Airport> items) {
        AirportCatalog fake = new AirportCatalog(items);
        return new AirportService(fake);
    }

    private List<Airport> sample() {
        return List.of(
            new Airport("WAW", "Warsaw Chopin Airport", "Warsaw", "Poland", 52.165, 20.967),
            new Airport("KRK", "Krakow John Paul II", "Krakow", "Poland", 50.077, 19.785),
            new Airport("DUB", "Dublin Airport", "Dublin", "Ireland", 53.421, -6.270),
            new Airport("FRA", "Frankfurt Airport", "Frankfurt", "Germany", 50.033, 8.570),
            new Airport("JFK", "John F Kennedy Intl", "New York", "United States", 40.639, -73.778)
        );
    }

    @Test
    void iataExactMatchFirst() {
        List<Airport> out = service(sample()).suggest("waw", 5);
        assertFalse(out.isEmpty());
        assertEquals("WAW", out.get(0).iata());
    }

    @Test
    void cityPrefix() {
        List<Airport> out = service(sample()).suggest("dub", 5);
        assertFalse(out.isEmpty());
        assertEquals("DUB", out.get(0).iata());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        assertTrue(service(sample()).suggest("", 5).isEmpty());
        assertTrue(service(sample()).suggest(null, 5).isEmpty());
    }

    @Test
    void nearestDublin() {
        List<Airport> near = service(sample()).nearest(53.3498, -6.2603, 2);
        assertNotNull(near);
        assertEquals("DUB", near.get(0).iata());
    }

    @Test
    void nearestWarsaw() {
        List<Airport> near = service(sample()).nearest(52.2297, 21.0122, 1);
        assertEquals("WAW", near.get(0).iata());
    }
}
