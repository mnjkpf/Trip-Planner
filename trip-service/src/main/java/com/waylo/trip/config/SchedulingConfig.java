package com.waylo.trip.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Вмикає @Scheduled — потрібне для OutboxPublisher.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
