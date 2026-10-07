package com.waylo.place.ownplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class GoogleMapsLinkParserTest {

    private final GoogleMapsLinkParser parser = new GoogleMapsLinkParser();

    @Test
    void parsesPlaceWithBangCoords() {
        String url = "https://www.google.com/maps/place/Trevi+Fountain/@41.9009,12.4833,17z/"
            + "data=!3m1!4b1!4m6!3m5!1s0x132f604f678df5a9:0xc8964b40c3c0e0ad!8m2!"
            + "3d41.9009306!4d12.4833317!16zL20vMDF4ZDRi";
        Optional<OwnPlacePreview> out = parser.parse(url);
        assertTrue(out.isPresent());
        // пин (!3d!4d) має перевагу над @ координатами
        assertEquals(41.9009306, out.get().lat(), 0.000001);
        assertEquals(12.4833317, out.get().lon(), 0.000001);
        assertEquals("Trevi Fountain", out.get().name());
    }

    @Test
    void parsesAtCoordWhenNoBang() {
        String url = "https://www.google.com/maps/place/Trevi+Fountain/@41.9009,12.4833,17z";
        var out = parser.parse(url);
        assertTrue(out.isPresent());
        assertEquals(41.9009, out.get().lat(), 0.0001);
        assertEquals(12.4833, out.get().lon(), 0.0001);
        assertEquals("Trevi Fountain", out.get().name());
    }

    @Test
    void parsesQCoord() {
        var out = parser.parse("https://maps.google.com/?q=53.3498,-6.2603");
        assertTrue(out.isPresent());
        assertEquals(53.3498, out.get().lat(), 0.0001);
        assertEquals(-6.2603, out.get().lon(), 0.0001);
        assertEquals("Власне місце", out.get().name());
    }

    @Test
    void returnsEmptyWhenNoCoords() {
        assertFalse(parser.parse("https://www.google.com/maps").isPresent());
        assertFalse(parser.parse("").isPresent());
        assertFalse(parser.parse(null).isPresent());
    }

    @Test
    void rejectsOutOfRangeCoords() {
        assertFalse(parser.parse("https://maps.google.com/?q=99.0,99.0").isPresent());
    }
}
