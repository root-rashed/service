package com.example.marketplace.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Verifies that the app.mail.* block binds onto {@link MailProperties}. */
class MailPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(MailProperties.class)
    static class PropsConfig {
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropsConfig.class);

    @Test
    void bindsProviderApiKeyAndFrom() {
        runner.withPropertyValues(
                        "app.mail.provider=brevo",
                        "app.mail.api-key=test_123",
                        "app.mail.from=ServiZone <test@example.com>")
                .run(context -> {
                    MailProperties properties = context.getBean(MailProperties.class);
                    assertThat(properties.getProvider()).isEqualTo("brevo");
                    assertThat(properties.getApiKey()).isEqualTo("test_123");
                    assertThat(properties.getFrom()).isEqualTo("ServiZone <test@example.com>");
                });
    }

    @Test
    void missingValuesLeaveSafeDefaults() {
        runner.run(context -> {
            MailProperties properties = context.getBean(MailProperties.class);
            assertThat(properties.getProvider()).isEqualTo("brevo");
            assertThat(properties.getApiKey()).isEmpty();
            assertThat(properties.getFrom()).isEmpty();
        });
    }
}