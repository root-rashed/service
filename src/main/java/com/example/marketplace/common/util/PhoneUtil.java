package com.example.marketplace.common.util;

import com.example.marketplace.common.exception.ValidationException;
import java.util.Map;
import java.util.regex.Pattern;

/** Phone number normalization shared by profile forms. */
public final class PhoneUtil {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private PhoneUtil() {
    }

    /** Strips spaces, dashes and brackets; throws a field error on the "phone" field if the result is not a phone number. */
    public static String normalize(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[\\s\\-()]", "");
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of("phone", "Please enter a valid phone number."));
        }
        return cleaned;
    }
}
