package com.rubenazo.buscaConciertos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods (e.g. {@code ScheduledSyncRunner}) across the api module.
 * Scheduling itself stays opt-in: schedules with no configured cron expression
 * (Spring's {@code CRON_DISABLED} default, "-") simply never fire.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
