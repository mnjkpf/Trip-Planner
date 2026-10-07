package com.waylo.context.advice;

/** Пункт маршруту так, як його бачить погодна порада: назва й категорія (PARK, MUSEUM, ...). */
public record PlannedPlace(String name, String category) {}
