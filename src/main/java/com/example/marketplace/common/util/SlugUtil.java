package com.example.marketplace.common.util;

import java.text.Normalizer;
import java.util.Locale;

/** Builds clean, URL-safe slugs, e.g. "Beauty & Wellness" -> "beauty-wellness". */
public final class SlugUtil {

    private static final int MAX_LENGTH = 80;

    private SlugUtil() {
    }

    /**
     * Returns a lower-case ASCII slug, or an empty string if the input has no usable characters
     * (for example text written only in a non-Latin script). Callers must supply a fallback.
     */
    public static String toSlug(String input) {
        if (input == null) {
            return "";
        }
        String slug = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }
}
