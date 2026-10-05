package com.waylo.offer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.waylo.offer.dto.FlightSearchResponse;

import tools.jackson.databind.json.JsonMapper;

class FlightOfferMapperTest {

    private final FlightOfferMapper mapper = new FlightOfferMapper(JsonMapper.builder().build());

    @Test
    void parsesBestAndOther() {
        String json = """
            {
              "best_flights": [
                {
                  "flights": [{
                    "departure_airport": {"id":"KBP","name":"Kyiv Boryspil","time":"2026-10-15 10:30"},
                    "arrival_airport":   {"id":"DUB","name":"Dublin","time":"2026-10-15 15:40"},
                    "duration": 310,
                    "airline": "Ryanair",
                    "airline_logo": "https://x/logo.png",
                    "flight_number": "FR 123",
                    "travel_class": "Economy",
                    "airplane": "Boeing 737"
                  }],
                  "layovers": [],
                  "total_duration": 310,
                  "price": 199.0,
                  "type": "Round trip",
                  "booking_token": "abc"
                }
              ],
              "other_flights": []
            }
            """;
        FlightSearchResponse out = mapper.parse(json, "EUR");
        assertEquals(1, out.best().size());
        assertTrue(out.other().isEmpty());
        var o = out.best().get(0);
        assertEquals(1, o.segments().size());
        assertEquals("KBP", o.segments().get(0).departureAirport());
        assertEquals("DUB", o.segments().get(0).arrivalAirport());
        assertEquals(310, o.totalDurationMinutes());
        assertEquals(0, o.layoverCount());
        assertEquals(199.0, o.price(), 0.001);
        assertEquals("EUR", o.currency());
        assertEquals("Round trip", o.type());
        assertNotNull(o.segments().get(0).airlineLogoUrl());
    }

    @Test
    void toleratesEmptyAndNullJson() {
        assertTrue(mapper.parse("", "EUR").best().isEmpty());
        assertTrue(mapper.parse(null, "EUR").best().isEmpty());
    }

    @Test
    void handlesMissingCarbonAndLayovers() {
        String json = """
            {"best_flights":[{"flights":[{
                "departure_airport":{"id":"A","name":"A","time":"t"},
                "arrival_airport":{"id":"B","name":"B","time":"t"},
                "duration":60,"airline":"X","airline_logo":"","flight_number":"1","travel_class":"E","airplane":""
            }],"total_duration":60,"price":"50","type":"One way"}]}
            """;
        var out = mapper.parse(json, "USD");
        assertEquals(1, out.best().size());
        assertEquals(50.0, out.best().get(0).price(), 0.001);
        assertEquals(0, out.best().get(0).layoverCount());
        assertFalse(out.best().get(0).segments().isEmpty());
    }
}
