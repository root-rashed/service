package com.example.marketplace.notification.email;

import brevo.ApiClient;
import brevo.ApiException;
import brevoApi.TransactionalEmailsApi;
import brevoModel.CreateSmtpEmail;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class BrevoEmailSender implements EmailSender {

    private final ApiClient apiClient;
    private final MailProperties properties;

    public BrevoEmailSender(ApiClient apiClient, MailProperties properties) {
        this.apiClient = apiClient;
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
            log.warn("Email '{}' not sent: app.mail.api-key (BREVO_API_KEY) is not configured.", message.subject());
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

        try {
            TransactionalEmailsApi api = new TransactionalEmailsApi(apiClient);
            SendSmtpEmail email = new SendSmtpEmail();
            email.setSender(parseSender(properties.getFrom(), properties.getFromName()));
            email.setTo(Collections.singletonList(new SendSmtpEmailTo().email(message.to())));
            email.setSubject(message.subject());
            if (StringUtils.hasText(message.text())) {
                email.setTextContent(message.text());
            }
            if (StringUtils.hasText(message.html())) {
                email.setHtmlContent(message.html());
            }
            CreateSmtpEmail response = api.sendTransacEmail(email);
            log.info("Email '{}' accepted by Brevo (messageId {}).", message.subject(), response.getMessageId());
        } catch (ApiException ex) {
            log.warn("Brevo rejected email '{}' (HTTP {}): {}", message.subject(), ex.getCode(),
                    ex.getResponseBody() == null ? "api_error" : ex.getResponseBody());
        } catch (RuntimeException ex) {
            log.warn("Email '{}' could not be sent ({}).", message.subject(), ex.getClass().getSimpleName());
        }
    }

    private SendSmtpEmailSender parseSender(String from, String fallbackName) {
        String f = from == null ? "" : from.trim();
        String fallback = StringUtils.hasText(fallbackName) ? fallbackName.trim() : null;
        int open = f.indexOf('<');
        if (open >= 0 && f.endsWith(">")) {
            String name = f.substring(0, open).trim().replaceAll("^\"|\"$", "");
            String email = f.substring(open + 1, f.length() - 1).trim();
            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            if (StringUtils.hasText(name)) sender.setName(name);
            else if (fallback != null) sender.setName(fallback);
            sender.setEmail(email);
            return sender;
        }
        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        if (fallback != null) sender.setName(fallback);
        sender.setEmail(f);
        return sender;
    }


}