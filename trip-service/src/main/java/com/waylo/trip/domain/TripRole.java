package com.waylo.trip.domain;

/**
 * Роль учасника подорожі. Порядок у переліку — від найсильнішої до найслабшої,
 * і перевірка доступу спирається саме на нього: OWNER може все, що EDITOR,
 * а EDITOR — усе, що VIEWER.
 */
public enum TripRole {
    OWNER,
    EDITOR,
    VIEWER;

    /** Чи вистачає цієї ролі там, де потрібна щонайменше {@code required}. */
    public boolean isAtLeast(TripRole required) {
        return ordinal() <= required.ordinal();
    }
}
