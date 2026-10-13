package com.example.marketplace.notification.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound from the {@code app.mail.*} properties in application.yml. The API key comes from
 * {@code BREVO_API_KEY} (via {@code app.mail.api-key}) and is never committed or logged.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    private String provider = "brevo";
    private String apiKey = "";
    private String from = "";
    private String fromName = "ServiZone";
}
