package com.example.marketplace.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ResendEmailSender}. The Resend SDK is mocked, so no network request is
 * made; a real send is only the SDK call that these tests stub.
 */
class ResendEmailSenderTest {

    private Emails emails;
    private MailProperties properties;

    @BeforeEach
    void setUp() {
        emails = mock(Emails.class);
        properties = new MailProperties();
        properties.setProvider("resend");
        properties.setApiKey("re_test_key");
        properties.setFrom("ServiZone <onboarding@resend.dev>");
    }

    private ResendEmailSender sender() {
        return new ResendEmailSender(emails, properties);
    }

    @Test
    void sendsFromRecipientSubjectAndTextBodyThroughResend() throws Exception {
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse("email_123"));

        sender().send("customer@example.com", "ServiZone: verify your email",
                "Use this code: 123456\nIt expires shortly.");

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("ServiZone <onboarding@resend.dev>");
        assertThat(sent.getTo()).containsExactly("customer@example.com");
        assertThat(sent.getSubject()).isEqualTo("ServiZone: verify your email");
        assertThat(sent.getText()).contains("123456");
        assertThat(sent.getHtml()).isNull();
    }

    @Test
    void sendsHtmlBodyWhenProvided() throws Exception {
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse("email_456"));

        sender().send(EmailMessage.html("worker@example.com", "ServiZone: booking confirmed", "<p>Hello</p>"));

        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions sent = captor.getValue();
        assertThat(sent.getHtml()).isEqualTo("<p>Hello</p>");
        assertThat(sent.getText()).isNull();
    }

    @Test
    void apiRejectionIsLoggedAndNotThrownToTheCaller() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(422, "{\"name\":\"validation_error\",\"message\":\"from is not verified\"}"));

        assertThatCode(() -> sender().send("customer@example.com", "Subject", "Body"))
                .doesNotThrowAnyException();
    }

    @Test
    void networkFailureIsLoggedAndNotThrownToTheCaller() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        assertThatCode(() -> sender().send("customer@example.com", "Subject", "Body"))
                .doesNotThrowAnyException();
    }

    @Test
    void missingApiKeySkipsSendingWithoutCallingTheSdk() throws Exception {
        properties.setApiKey("  ");

        sender().send("customer@example.com", "Subject", "Body");

        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void missingFromSkipsSending() throws Exception {
        properties.setFrom("");

        sender().send("customer@example.com", "Subject", "Body");

        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void blankRecipientSkipsSending() throws Exception {
        sender().send(EmailMessage.text("  ", "Subject", "Body"));

        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void messageWithoutABodyIsNotSent() throws Exception {
        sender().send(new EmailMessage("customer@example.com", "Subject", null, null));

        verify(emails, never()).send(any(CreateEmailOptions.class));
    }
}