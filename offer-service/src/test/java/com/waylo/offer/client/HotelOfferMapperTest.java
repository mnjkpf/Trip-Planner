package com.waylo.offer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class HotelOfferMapperTest {

    private final HotelOfferMapper mapper = new HotelOfferMapper(JsonMapper.builder().build());

    @Test
    void parsesProperties() {
        String json = """
            {"properties":[{
              "property_token":"tok1","name":"The Shelbourne","type":"hotel","link":"https://x",
              "gps_coordinates":{"latitude":53.3395,"longitude":-6.2561},
              "overall_rating":4.5,"reviews":1240,"hotel_class":5,
              "amenities":["Wi-Fi","Pool"],
              "images":[{"thumbnail":"https://t","original_image":"https://o"}],
              "rate_per_night":{"extracted_lowest":320,"lowest":"€320"},
              "total_rate":{"extracted_lowest":2240,"lowest":"€2240"},
              "check_in_time":"3:00 PM","check_out_time":"11:00 AM","description":"Historic hotel"
            }]}
            """;
        var out = mapper.parse(json, "EUR");
        assertEquals(1, out.properties().size());
        var h = out.properties().get(0);
        assertEquals("The Shelbourne", h.name());
        assertEquals(5, h.hotelClass());
        assertEquals(4.5, h.rating(), 0.001);
        assertEquals(1240, h.reviewsCount());
        assertEquals(320.0, h.ratePerNight(), 0.001);
        assertEquals(2240.0, h.totalRate(), 0.001);
        assertEquals("EUR", h.currency());
        assertEquals(53.3395, h.lat(), 1e-6);
        assertEquals(-6.2561, h.lon(), 1e-6);
        assertNotNull(h.imageUrls());
        assertEquals("https://o", h.imageUrls().get(0));
    }

    @Test
    void toleratesEmpty() {
        assertTrue(mapper.parse("{}", "EUR").properties().isEmpty());
        assertTrue(mapper.parse("", "EUR").properties().isEmpty());
    }
}
