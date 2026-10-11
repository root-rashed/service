package com.example.marketplace.admin.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Small helpers shared by the admin controllers.
 *
 * The admin tables keep their filters in the URL so a list stays shareable and the browser's
 * back button works. Building that query string here — rather than in a Thymeleaf expression —
 * keeps URL encoding in one place and out of the templates.
 */
public final class AdminFilters {

    private AdminFilters() {
    }

    /**
     * Builds the already-encoded {@code "k=v&k=v"} string the pagination fragment expects.
     *
     * <p>Blank values are dropped, so "no filters selected" produces an empty string and the
     * fragment then builds a clean {@code ?page=N} link.
     *
     * @param keyValues alternating key and value; an odd trailing key is ignored
     */
    public static String query(String... keyValues) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String value = keyValues[i + 1];
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append('&');
            }
            result.append(encode(keyValues[i])).append('=').append(encode(value));
        }
        return result.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
