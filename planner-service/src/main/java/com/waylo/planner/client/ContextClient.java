package com.waylo.planner.client;

import java.time.LocalDate;

/** Абстракція над context-service — щоб у тестах підмінити заглушкою. */
public interface ContextClient {
    DestinationContext fetch(double lat, double lon, LocalDate start, LocalDate end);
}
