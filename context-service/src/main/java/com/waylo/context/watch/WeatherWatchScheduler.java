package com.waylo.context.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Плановий запуск нагляду. Окремий бін, щоб у тестах вимкнути розклад
 * (app.weather-watch.enabled=false) і викликати сервіс напряму, без гонок.
 *
 * Перший прохід — невдовзі після старту: після рестарту поради оновляться,
 * не чекаючи шість годин.
 */
@Component
@ConditionalOnProperty(name = "app.weather-watch.enabled", havingValue = "true", matchIfMissing = true)
public class WeatherWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeatherWatchScheduler.class);

    private final WeatherWatchService watchService;

    public WeatherWatchScheduler(WeatherWatchService watchService) {
        this.watchService = watchService;
    }

    @Scheduled(initialDelayString = "${app.weather-watch.initial-delay:PT1M}",
               fixedDelayString = "${app.weather-watch.interval:PT6H}")
    public void run() {
        try {
            watchService.checkAll(LocalDate.now());
        } catch (RuntimeException ex) {
            log.warn("погодний нагляд упав: {}", ex.getMessage());
        }
    }
}
