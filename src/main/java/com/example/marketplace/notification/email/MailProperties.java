package com.example.marketplace.notification.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound from the {@code app.mail.*} properties in application.yml, replacing the old
 * {@code spring.mail.*} SMTP settings. The API key comes from {@code RESEND_API_KEY} (via
 * {@code app.mail.api-key}) and is never committed or logged.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    /** Email provider. Only "resend" is implemented. */
    private String provider = "resend";

    /** Resend API key. Read from RESEND_API_KEY; never commit it to source control. */
    private String apiKey = "";

    /** Verified sender address, e.g. "ServiZone <no-reply@servizone.com>". */
    private String from = "";
}
