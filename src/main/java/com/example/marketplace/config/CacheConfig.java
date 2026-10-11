package com.example.marketplace.config;

import com.example.marketplace.cache.CacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple in-memory cache built on Spring's cache abstraction.
 * The CacheManager bean is the only thing to swap if a different
 * cache provider is ever needed.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                CacheNames.CATEGORIES,
                CacheNames.POPULAR_SERVICES,
                CacheNames.WORKER_PROFILES,
                CacheNames.SERVICE_DETAILS
        );
    }
}
