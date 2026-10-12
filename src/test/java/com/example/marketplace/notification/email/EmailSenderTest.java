package com.example.marketplace.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The {@code EmailSender} contract: the plain-text convenience must produce a text message. */
class EmailSenderTest {

    @Test
    void threeArgumentConvenienceDelegatesToATextMessage() {
        CapturingEmailSender sender = new CapturingEmailSender();

        sender.send("to@example.com", "Subject", "Body text");

        assertThat(sender.sent()).hasSize(1);
        EmailMessage message = sender.sent().get(0);
        assertThat(message.to()).isEqualTo("to@example.com");
        assertThat(message.subject()).isEqualTo("Subject");
        assertThat(message.text()).isEqualTo("Body text");
        assertThat(message.html()).isNull();
    }
}