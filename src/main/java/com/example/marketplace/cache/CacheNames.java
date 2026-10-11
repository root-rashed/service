package com.example.marketplace.cache;

/**
 * Central registry of Spring Cache names. Use these constants in
 * {@code @Cacheable}, {@code @CachePut} and {@code @CacheEvict}.
 *
 * Never cache OTPs, passwords, payment state, live booking state or availability.
 */
public final class CacheNames {

    public static final String CATEGORIES = "categories";
    public static final String POPULAR_SERVICES = "popularServices";
    public static final String WORKER_PROFILES = "workerProfiles";
    public static final String SERVICE_DETAILS = "serviceDetails";

    private CacheNames() {
    }
}
