package com.example.marketplace.config;

import com.example.marketplace.notification.email.MailProperties;
import com.resend.Resend;
import com.resend.services.emails.Emails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds the Resend SDK client from {@code app.mail.*} configuration.
 *
 * Constructing the client performs no network call, so the application still starts for local
 * development without a key; in that case every send is skipped with a logged warning. The API
 * key itself is never logged.
 */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public Emails resendEmails(MailProperties properties) {
        if (!"resend".equals(properties.getProvider())) {
            log.warn("app.mail.provider '{}' is not supported; only 'resend' is implemented.",
                    properties.getProvider());
        }
        if (StringUtils.hasText(properties.getApiKey())) {
            log.info("Outgoing email is configured through Resend (from={}).", properties.getFrom());
        } else {
            log.warn("app.mail.api-key (RESEND_API_KEY) is not configured: outgoing email is disabled.");
        }
        return new Resend(properties.getApiKey()).emails();
    }
}
