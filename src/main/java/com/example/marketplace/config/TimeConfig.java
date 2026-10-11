package com.example.marketplace.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One business time zone for the whole marketplace (working hours, "today", booking dates).
 * Inject the Clock instead of calling LocalDate.now() so time-dependent rules stay testable.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Dhaka}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
