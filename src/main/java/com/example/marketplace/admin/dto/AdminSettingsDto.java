package com.example.marketplace.admin.dto;

import java.math.BigDecimal;
import lombok.Value;

/**
 * Read-only view of the settings that shape how the marketplace behaves.
 *
 * Secrets (Mongo URI, mail password, OTP key) are deliberately absent: configuration is
 * supplied through environment variables and is never echoed back into a page.
 */
@Value
public class AdminSettingsDto {

    String appName;
    String baseUrl;
    String timezone;

    BigDecimal platformFeePercent;
    int cancelCutoffHours;
    int minNoticeMinutes;
    int maxAdvanceDays;
    int slotStepMinutes;

    int maxLoginAttempts;
    int loginLockMinutes;

    int otpExpiryMinutes;
    int otpMaxAttempts;
    int otpResendCooldownSeconds;
    int otpMaxPerHour;

    String activeProfile;
    String ollamaBaseUrl;
    String ollamaModel;

    /** Always "CASH": this build has no online payment provider at all. */
    String paymentMethod;
}