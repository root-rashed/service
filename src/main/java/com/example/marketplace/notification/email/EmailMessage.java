package com.example.marketplace.notification.email;

/**
 * One outgoing email. A plain-text body, an HTML body, or both may be set; every current
 * workflow sends plain text. Recipients are always a single address: no existing feature
 * sends to several recipients or attaches files.
 */
public record EmailMessage(String to, String subject, String text, String html) {

    /** Convenience factory for the plain-text emails used across the application. */
    public static EmailMessage text(String to, String subject, String text) {
        return new EmailMessage(to, subject, text, null);
    }

    public static EmailMessage html(String to, String subject, String html) {
        return new EmailMessage(to, subject, null, html);
    }
}