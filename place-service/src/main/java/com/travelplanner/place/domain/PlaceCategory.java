package com.travelplanner.place.domain;

/**
 * Внутрішні категорії місць. Кожен провайдер має власну таксономію —
 * маппер провайдера зводить її до цього переліку (це робота наступного кроку).
 */
public enum PlaceCategory {
    HOTEL,
    RESTAURANT,
    CAFE,
    BAR,
    MUSEUM,
    ATTRACTION,
    PARK,
    BEACH,
    SHOP,
    OTHER
}
