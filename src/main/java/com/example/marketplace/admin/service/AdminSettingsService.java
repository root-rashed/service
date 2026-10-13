package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminSettingsDto;
import com.example.marketplace.common.enums.PaymentMethod;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Exposes the non-secret runtime settings on the admin settings page.
 *
 * This is a read-only projection: nothing here can be edited in the browser, so every value
 * still comes from configuration. Secrets are never resolved into the DTO.
 */
@Service
public class AdminSettingsService {

    private final Environment environment;
    private final String activeProfile;

    public AdminSettingsService(Environment environment,
                                @Value("${spring.profiles.active:default}") String activeProfile) {
        this.environment = environment;
        this.activeProfile = activeProfile;
    }

    public AdminSettingsDto getSettings() {
        return new AdminSettingsDto(
                property("app.name", "ServiZone"),
                property("app.base-url", ""),
                property("app.timezone", ""),
                decimal("app.booking.platform-fee-percent"),
                number("app.booking.cancel-cutoff-hours"),
                number("app.booking.min-notice-minutes"),
                number("app.booking.max-advance-days"),
                number("app.booking.slot-step-minutes"),
                number("app.security.max-login-attempts"),
                number("app.security.lock-minutes"),
                number("app.otp.expiry-minutes"),
                number("app.otp.max-attempts"),
                number("app.otp.resend-cooldown-seconds"),
                number("app.otp.max-per-hour"),
                activeProfile,
                property("spring.ai.google.genai.chat.model", property("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash")),
                PaymentMethod.CASH.name());
    }

    private String property(String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    private int number(String key) {
        return environment.getProperty(key, Integer.class, 0);
    }

    private BigDecimal decimal(String key) {
        return environment.getProperty(key, BigDecimal.class, BigDecimal.ZERO);
    }
}