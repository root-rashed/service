package com.example.marketplace.notification.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double that records every email an {@link EmailSender} would send, without touching the
 * network. It implements the interface directly, so the plain-text convenience method's default
 * delegation is exercised exactly as it is in production.
 */
public class CapturingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new ArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return sent;
    }
}