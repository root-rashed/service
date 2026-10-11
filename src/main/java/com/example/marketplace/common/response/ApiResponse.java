package com.example.marketplace.common.response;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Uniform JSON envelope for AJAX / API endpoints (favorites, chatbot, slots...).
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, Collections.emptyMap(), Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, Collections.emptyMap(), Instant.now());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, Collections.emptyMap(), Instant.now());
    }

    public static ApiResponse<Void> error(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }
}
