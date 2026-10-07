package com.waylo.offer.dto;

import java.util.List;

/** Готелі + оренда разом; клієнт фільтрує за полем {@code type}. */
public record HotelSearchResponse(
    List<HotelOffer> properties
) {}
