package com.waylo.trip.domain;

/** Категорії витрат. Перелік закритий — він же в CHECK-обмеженні міграції V6. */
public enum ExpenseCategory {
    FLIGHT,
    HOTEL,
    FOOD,
    TRANSPORT,
    ACTIVITY,
    SHOPPING,
    OTHER
}
