package com.waylo.offer.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.waylo.offer.client.FlightOfferMapper;
import com.waylo.offer.client.SerpApiClient;
import com.waylo.offer.dto.FlightSearchResponse;

@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final SerpApiClient serp;
    private final FlightOfferMapper mapper;

    public FlightService(SerpApiClient serp, FlightOfferMapper mapper) {
        this.serp = serp;
        this.mapper = mapper;
    }

    public FlightSearchResponse search(
            String from, String to, LocalDate depart, LocalDate returnDate,
            int adults, String currency) {
        String ccy = currency == null || currency.isBlank() ? serp.defaultCurrency() : currency;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("departure_id", from.toUpperCase());
        params.put("arrival_id", to.toUpperCase());
        params.put("outbound_date", depart.toString());
        if (returnDate != null) {
            params.put("return_date", returnDate.toString());
            params.put("type", "1");
        } else {
            params.put("type", "2");
        }
        params.put("adults", Integer.toString(adults));
        params.put("currency", ccy);
        params.put("hl", "en");
        String body;
        try {
            body = serp.search("google_flights", params);
        } catch (Exception ex) {
            log.warn("google_flights {}→{} ({}): {}", from, to, depart, ex.getMessage());
            return new FlightSearchResponse(List.of(), List.of());
        }
        FlightSearchResponse out = mapper.parse(body, ccy);
        log.info("google_flights {}→{} {}..{} : best={} other={}",
            from, to, depart, returnDate, out.best().size(), out.other().size());
        return out;
    }
}
