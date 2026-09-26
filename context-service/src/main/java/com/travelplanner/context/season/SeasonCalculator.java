package com.travelplanner.context.season;

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

    public String hint(Season season) {
        return switch (season) {
            case WINTER -> "Холодно й короткий світловий день — тепло вдягайся, більше плануй у приміщенні.";
            case SPRING -> "Мінлива погода — бери шар одягу й парасолю про запас.";
            case SUMMER -> "Спекотно вдень — активність краще зранку та ввечері, бери воду й крем від сонця.";
            case AUTUMN -> "Прохолодно й можливі дощі — зручне взуття та вітрівка стануть у пригоді.";
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
