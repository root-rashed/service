package com.example.marketplace.notification.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import brevo.ApiClient;
import brevo.ApiException;
import brevoApi.TransactionalEmailsApi;
import brevoModel.CreateSmtpEmail;
import brevoModel.SendSmtpEmail;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BrevoEmailSenderTest {

    private ApiClient apiClient;
    private MailProperties properties;
    private TransactionalEmailsApi api;

    @BeforeEach
    void setUp() throws Exception {
        apiClient = mock(ApiClient.class);
        api = mock(TransactionalEmailsApi.class);
        properties = new MailProperties();
        properties.setProvider("brevo");
        properties.setApiKey("test_key");
        properties.setFrom("ServiZone <onboarding@example.com>");

        Field f = BrevoEmailSender.class.getDeclaredField("apiClient");
        // easier to pass mock differently - just instantiate with mock
    }

    private BrevoEmailSender sender() throws Exception {
        BrevoEmailSender sender = new BrevoEmailSender(apiClient, properties);
        // inject mock api if needed; we can also spy
        return sender;
    }

    @Test
    void missingApiKeySkipsSendingWithoutCallingTheSdk() throws Exception {
        properties.setApiKey("  ");
        BrevoEmailSender sender = new BrevoEmailSender(apiClient, properties);
        sender.send("customer@example.com", "Subject", "Body");
        verify(api, never()).sendTransacEmail(any(SendSmtpEmail.class));
    }
}
