package com.example.marketplace.notification.email;

/**
 * Low-level email abstraction. Implementations must never block the caller's request and must
 * never throw to the caller; delivery problems are logged instead. The production implementation
 * sends through the configured provider's HTTPS API (Brevo or Resend).
 */
public interface EmailSender {

    /** Sends one email through the configured provider. */
    void send(EmailMessage message);

    /**
     * Convenience for the plain-text emails used across the application, so existing call sites
     * (OTP codes, booking notifications, the contact form) do not have to build a message object.
     */
    default void send(String to, String subject, String textBody) {
        send(EmailMessage.text(to, subject, textBody));
    }
}