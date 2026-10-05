package com.waylo.offer.dto;

import java.util.List;

/**
 * Один готель з Google Hotels. Ціна — за всю заявлену добу/період; саме так
 * повертає SerpAPI (total_rate.extracted_lowest). Якщо потрібна ціна за ніч —
 * беремо rate_per_night (ratePerNight).
 */
public record HotelOffer(
    String id,                   // property_token з SerpAPI (унікальний)
    String name,
    String type,                 // Hotel / Vacation rental / тощо
    String link,
    Double lat,
    Double lon,
    Double rating,               // 0..5
    Integer reviewsCount,
    Integer hotelClass,          // кількість зірок (null якщо немає)
    List<String> amenities,
    List<String> imageUrls,
    Double totalRate,            // ціна за весь період, у валюті запиту
    Double ratePerNight,
    String currency,
    String checkInTime,
    String checkOutTime,
    String description
) {}
