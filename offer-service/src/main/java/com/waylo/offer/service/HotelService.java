package com.waylo.offer.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.waylo.offer.client.HotelOfferMapper;
import com.waylo.offer.client.SerpApiClient;
import com.waylo.offer.dto.HotelSearchResponse;

@Service
public class HotelService {

    private static final Logger log = LoggerFactory.getLogger(HotelService.class);

    private final SerpApiClient serp;
    private final HotelOfferMapper mapper;

    public HotelService(SerpApiClient serp, HotelOfferMapper mapper) {
        this.serp = serp;
        this.mapper = mapper;
    }

    public HotelSearchResponse search(
            String query, LocalDate checkIn, LocalDate checkOut,
            int adults, String currency) {
        String ccy = currency == null || currency.isBlank() ? serp.defaultCurrency() : currency;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("check_in_date", checkIn.toString());
        params.put("check_out_date", checkOut.toString());
        params.put("adults", Integer.toString(adults));
        params.put("currency", ccy);
        params.put("hl", "en");
        String body;
        try {
            body = serp.search("google_hotels", params);
        } catch (Exception ex) {
            log.warn("google_hotels '{}' {}..{}: {}", query, checkIn, checkOut, ex.getMessage());
            return new HotelSearchResponse(List.of());
        }
        HotelSearchResponse out = mapper.parse(body, ccy);
        log.info("google_hotels '{}' {}..{} : {} properties", query, checkIn, checkOut, out.properties().size());
        return out;
    }
}
