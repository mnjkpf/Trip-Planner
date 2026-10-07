package com.waylo.context.advice;

import java.time.LocalDate;
import java.util.List;

/** День маршруту: номер, дата й пункти в порядку відвідування. */
public record PlannedDay(int dayIndex, LocalDate date, List<PlannedPlace> places) {}
