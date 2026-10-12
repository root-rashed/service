package com.example.marketplace.notification.email;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Sends outgoing mail through Resend's HTTPS API using the official Resend Java SDK.
 *
 * Delivery is asynchronous and failures are logged, never thrown: an email problem must not roll
 * back the business action that produced the message. The API response is only ever an
 * <em>acceptance</em> for delivery, never proof of inbox delivery.
 *
 * The API key is read from configuration ({@code app.mail.api-key}, usually exported as
 * {@code RESEND_API_KEY}) and is never logged or echoed in error messages.
 */
@Slf4j
@Component
public class ResendEmailSender implements EmailSender {

    private final Emails emails;
    private final MailProperties properties;

    public ResendEmailSender(Emails emails, MailProperties properties) {
        this.emails = emails;
        this.properties = properties;
    }

    @Async
    @Override
    public void send(EmailMessage message) {
        if (message == null) {
            log.warn("Email not sent: no message was supplied.");
            return;
        }
        if (message.to() == null || message.to().isBlank()) {
            log.warn("Email '{}' not sent: no recipient address was supplied.", message.subject());
            return;
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.warn("Email '{}' not sent: app.mail.api-key (RESEND_API_KEY) is not configured.", message.subject());
            return;
        }
        if (!StringUtils.hasText(properties.getFrom())) {
            log.warn("Email '{}' not sent: app.mail.from (MAIL_FROM) is not configured.", message.subject());
            return;
        }
        if (!StringUtils.hasText(message.text()) && !StringUtils.hasText(message.html())) {
            log.warn("Email '{}' not sent: it has neither a text nor an HTML body.", message.subject());
            return;
        }

        CreateEmailOptions.Builder options = CreateEmailOptions.builder()
                .from(properties.getFrom())
                .to(message.to())
                .subject(message.subject());
        if (StringUtils.hasText(message.text())) {
            options.text(message.text());
        }
        if (StringUtils.hasText(message.html())) {
            options.html(message.html());
        }

        try {
            CreateEmailResponse response = emails.send(options.build());
            // Accepted by Resend for delivery; not a guarantee the message reached an inbox.
            log.info("Email '{}' accepted by Resend (message id {}).", message.subject(), response.getId());
        } catch (ResendException ex) {
            // The API refused the request: authentication, validation, rate limit, ...
            String status = ex.getStatusCode() == null ? "-" : String.valueOf(ex.getStatusCode());
            String error = ex.getErrorName() == null ? "api_error" : ex.getErrorName();
            log.warn("Resend rejected email '{}' (HTTP {} {}): {}",
                    message.subject(), status, error, safeMessage(ex));
        } catch (RuntimeException ex) {
            // Network failure or SDK error before a response arrived.
            log.warn("Email '{}' could not be sent ({}).", message.subject(), ex.getClass().getSimpleName());
        }
    }

    /** The API message is diagnostic only and never contains the API key. */
    private String safeMessage(ResendException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "see the Resend dashboard for details" : message;
    }
}