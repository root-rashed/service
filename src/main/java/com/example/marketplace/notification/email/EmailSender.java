package com.example.marketplace.notification.email;

/** Low-level email abstraction. Implementations must never block the caller's request. */
public interface EmailSender {

    void send(String to, String subject, String textBody);
}
