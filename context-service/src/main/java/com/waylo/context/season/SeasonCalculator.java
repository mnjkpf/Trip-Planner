package com.waylo.context.season;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Сезон за місяцем і півкулею (метеорологічні сезони). Для південної півкулі
 * (latitude < 0) сезон протилежний. Чиста логіка — без мережі, легко тестити.
 */
@Component
public class SeasonCalculator {

    public Season seasonFor(LocalDate date, double latitude) {
        Season northern = switch (date.getMonthValue()) {
            case 12, 1, 2 -> Season.WINTER;
            case 3, 4, 5 -> Season.SPRING;
            case 6, 7, 8 -> Season.SUMMER;
            default -> Season.AUTUMN;          // 9, 10, 11
        };
        return latitude >= 0 ? northern : opposite(northern);
    }

    /** Код підказки для клієнта — його і треба перекладати на боці UI. */
    public ClimateHint hintCode(Season season) {
        return switch (season) {
            case WINTER -> ClimateHint.COLD_SHORT_DAYS;
            case SPRING -> ClimateHint.CHANGEABLE;
            case SUMMER -> ClimateHint.HOT;
            case AUTUMN -> ClimateHint.COOL_RAINY;
        };
    }

    /** Фолбек-текст англійською, якщо клієнт не вміє в коди. */
    public String hint(Season season) {
        return switch (season) {
            case WINTER -> "Cold with short daylight hours \u2014 dress warm and plan more indoor stops.";
            case SPRING -> "Changeable weather \u2014 bring an extra layer and an umbrella just in case.";
            case SUMMER -> "Hot during the day \u2014 go out in the morning and evening, take water and sunscreen.";
            case AUTUMN -> "Cool with likely rain \u2014 comfortable shoes and a windbreaker will come in handy.";
        };
    }

    private Season opposite(Season s) {
        return switch (s) {
            case WINTER -> Season.SUMMER;
            case SUMMER -> Season.WINTER;
            case SPRING -> Season.AUTUMN;
            case AUTUMN -> Season.SPRING;
        };
    }
}
