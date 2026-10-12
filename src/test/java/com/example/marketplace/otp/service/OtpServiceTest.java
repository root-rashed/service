package com.example.marketplace.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketplace.common.enums.OtpPurpose;
import com.example.marketplace.common.exception.OtpException;
import com.example.marketplace.notification.email.CapturingEmailSender;
import com.example.marketplace.notification.email.EmailMessage;
import com.example.marketplace.otp.config.OtpProperties;
import com.example.marketplace.otp.model.Otp;
import com.example.marketplace.otp.repository.OtpRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pins that the OTP workflow still generates a code, stores only its hash, and hands the
 * plaintext code to the email sender.
 */
class OtpServiceTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    private OtpRepository otpRepository;
    private CapturingEmailSender emailSender;
    private OtpProperties properties;
    private OtpService service;

    @BeforeEach
    void setUp() {
        otpRepository = org.mockito.Mockito.mock(OtpRepository.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        emailSender = new CapturingEmailSender();
        properties = new OtpProperties();
        properties.setSecret("test-otp-secret");
        Environment environment = new MockEnvironment();
        service = new OtpService(otpRepository, mongoTemplate, emailSender, properties, environment, "ServiZone");
    }

    @Test
    void issueAndSendEmailsANormalizedRecipientWithTheGeneratedCode() {
        when(otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndPurposeAndCreatedAtAfter(anyString(), any(), any()))
                .thenReturn(0L);

        service.issueAndSend(" User@Example.com ", "Rashid", OtpPurpose.REGISTRATION);

        assertThat(emailSender.sent()).hasSize(1);
        EmailMessage email = emailSender.sent().get(0);
        assertThat(email.to()).isEqualTo("user@example.com");
        assertThat(email.subject()).isEqualTo("ServiZone: verify your email");
        assertThat(email.text()).contains("Hi Rashid,");
        assertThat(email.text()).contains("verify your email address");

        Matcher matcher = SIX_DIGITS.matcher(email.text());
        assertThat(matcher.find()).isTrue();
        String code = matcher.group(1);

        ArgumentCaptor<Otp> stored = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(stored.capture());
        // Only the HMAC hash is persisted, never the plaintext code.
        assertThat(stored.getValue().getOtpHash()).hasSize(64).isNotEqualTo(code);
    }

    @Test
    void resendWithinCooldownThrowsAndSendsNothing() {
        Otp latest = Otp.builder()
                .email("user@example.com")
                .purpose(OtpPurpose.REGISTRATION)
                .otpHash("previous")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.issueAndSend("user@example.com", "Rashid", OtpPurpose.REGISTRATION))
                .isInstanceOf(OtpException.class);

        assertThat(emailSender.sent()).isEmpty();
        verify(otpRepository, never()).save(any());
    }

    @Test
    void hourlyCapBlocksAndSendsNothing() {
        when(otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndPurposeAndCreatedAtAfter(anyString(), any(), any()))
                .thenReturn((long) properties.getMaxPerHour());

        assertThatThrownBy(() -> service.issueAndSend("user@example.com", "Rashid", OtpPurpose.PASSWORD_RESET))
                .isInstanceOf(OtpException.class);

        assertThat(emailSender.sent()).isEmpty();
        verify(otpRepository, never()).save(any());
    }
}