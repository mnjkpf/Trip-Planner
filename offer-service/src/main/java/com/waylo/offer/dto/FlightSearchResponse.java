package com.waylo.offer.dto;

import java.util.List;

/** Повна відповідь: кращі + інші пропозиції; усе вже змаплено у наш формат. */
public record FlightSearchResponse(
    List<FlightOffer> best,
    List<FlightOffer> other
) {}
