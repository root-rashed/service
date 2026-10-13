package com.example.marketplace.config;

import com.example.marketplace.notification.email.MailProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public HttpClient mailHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Bean
    public brevo.ApiClient brevoApiClient(MailProperties properties) {
        brevo.ApiClient apiClient = brevo.Configuration.getDefaultApiClient();
        String key = properties.getApiKey();
        if (StringUtils.hasText(key)) {
            apiClient.setApiKey(key);
            apiClient.addDefaultHeader("api-key", key);
            log.info("Outgoing email is configured through Brevo (from={}).", properties.getFrom());
        } else {
            log.warn("app.mail.api-key (BREVO_API_KEY) is not configured: outgoing email is disabled.");
        }
        return apiClient;
    }
}
