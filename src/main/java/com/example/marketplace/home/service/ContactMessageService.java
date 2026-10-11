package com.example.marketplace.home.service;

import com.example.marketplace.home.dto.ContactForm;
import com.example.marketplace.notification.email.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Forwards contact-form submissions to the support mailbox.
 *
 * There is no contact-message collection on purpose: the form is a thin mail relay, so
 * nothing a visitor typed is persisted by this module. Delivery is delegated to the
 * application's {@link EmailSender}, which runs on its own executor, so a slow SMTP
 * server can never delay the response.
 */
@Service
public class ContactMessageService {

    /** Guards against a visitor flooding the mailbox with one-line messages. */
    private static final int MAX_LENGTHS_MESSAGE = 2000;

    private final EmailSender emailSender;
    private final String recipient;
    private final String appName;

    public ContactMessageService(EmailSender emailSender,
                                 @Value("${app.contact.to:}") String recipient,
                                 @Value("${app.name:ServiZone}") String appName) {
        this.emailSender = emailSender;
        this.recipient = recipient == null ? "" : recipient.trim();
        this.appName = appName;
    }

    /**
     * @return true when the message was handed to the mail sender, false when no
     *         support mailbox is configured (the page still confirms receipt so the
     *         visitor is not told about server configuration).
     */
    public boolean send(ContactForm form) {
        if (recipient.isEmpty()) {
            return false;
        }
        StringBuilder body = new StringBuilder()
                .append("Message from the ").append(appName).append(" contact form\n")
                .append("---------------------------------------------\n")
                .append("Name:    ").append(trim(form.getName())).append('\n')
                .append("Email:   ").append(trim(form.getEmail())).append('\n');
        if (hasText(form.getPhone())) {
            body.append("Phone:   ").append(trim(form.getPhone())).append('\n');
        }
        body.append("Subject: ").append(trim(form.getSubject())).append("\n\n")
                .append(limit(form.getMessage()));
        emailSender.send(recipient, "[" + appName + "] " + trim(form.getSubject()), body.toString());
        return true;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String limit(String message) {
        String trimmed = trim(message);
        return trimmed.length() > MAX_LENGTHS_MESSAGE
                ? trimmed.substring(0, MAX_LENGTHS_MESSAGE) + "\n[truncated]"
                : trimmed;
    }
}