package com.example.marketplace.otp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bound from the app.otp.* properties in application.yml. */
@Component
@ConfigurationProperties(prefix = "app.otp")
@Getter
@Setter
public class OtpProperties {

    /** Server-side secret used to HMAC codes. Set OTP_SECRET in production. */
    private String secret = "";

    private int expiryMinutes = 10;

    private int maxAttempts = 5;

    private int resendCooldownSeconds = 60;

    /** Maximum codes issued per email and purpose within one hour. */
    private int maxPerHour = 5;
}
