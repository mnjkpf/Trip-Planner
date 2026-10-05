package com.waylo.place.provider;

/**
 * Результат збагачення фото від провайдера деталей (Geoapify Place Details).
 * imageUrl — пряме посилання на зображення (Wikimedia/Wikidata); wikidataId —
 * ідентифікатор Wikidata, якщо провайдер його повернув.
 */
public record PlaceImage(String imageUrl, String wikidataId) {}
