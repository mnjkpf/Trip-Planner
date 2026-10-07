package com.waylo.context.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Вмикає @Scheduled — потрібне для WeatherWatchScheduler.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
