package com.example.marketplace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async (used for non-blocking email delivery). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
