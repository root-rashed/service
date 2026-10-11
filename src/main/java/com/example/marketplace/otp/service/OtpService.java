package com.example.marketplace.otp.service;

import com.example.marketplace.common.enums.OtpPurpose;
import com.example.marketplace.common.exception.OtpException;
import com.example.marketplace.notification.email.EmailSender;
import com.example.marketplace.otp.config.OtpProperties;
import com.example.marketplace.otp.model.Otp;
import com.example.marketplace.otp.repository.OtpRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * OTP issuing and verification.
 * - 6-digit codes from SecureRandom, stored only as HMAC-SHA256 (keyed with a server secret).
 * - Expiry, attempt limit (claimed atomically), resend cooldown and hourly cap.
 * - The plaintext code exists only in memory and in the outgoing email; it is never logged.
 */
@Service
public class OtpService {

    private static final String DEV_SECRET = "dev-only-otp-secret-change-me";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OtpRepository otpRepository;
    private final MongoTemplate mongoTemplate;
    private final EmailSender emailSender;
    private final OtpProperties properties;
    private final String appName;
    private final byte[] secret;

    public OtpService(OtpRepository otpRepository,
                      MongoTemplate mongoTemplate,
                      EmailSender emailSender,
                      OtpProperties properties,
                      Environment environment,
                      @Value("${app.name:ServiZone}") String appName) {
        this.otpRepository = otpRepository;
        this.mongoTemplate = mongoTemplate;
        this.emailSender = emailSender;
        this.properties = properties;
        this.appName = appName;

        String configured = properties.getSecret();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("app.otp.secret (OTP_SECRET) must be configured.");
        }
        if (DEV_SECRET.equals(configured) && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("OTP_SECRET must be set to a unique value in production.");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Issues a new code and emails it. Enforces the resend cooldown and hourly cap,
     * so it is safe to use for both the first send and "resend code".
     */
    public void issueAndSend(String email, String recipientName, OtpPurpose purpose) {
        String normalized = normalize(email);
        Instant now = Instant.now();

        otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalized, purpose).ifPresent(latest -> {
            long waitSeconds = Duration.between(
                    now, latest.getCreatedAt().plusSeconds(properties.getResendCooldownSeconds())).getSeconds();
            if (waitSeconds > 0) {
                throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + waitSeconds + " seconds before requesting a new code.");
            }
        });

        long recentCount = otpRepository.countByEmailAndPurposeAndCreatedAtAfter(
                normalized, purpose, now.minus(1, ChronoUnit.HOURS));
        if (recentCount >= properties.getMaxPerHour()) {
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many code requests. Please try again later.");
        }

        String code = generateCode();
        otpRepository.save(Otp.builder()
                .email(normalized)
                .purpose(purpose)
                .otpHash(hash(normalized, purpose, code))
                .createdAt(now)
                .expiresAt(now.plus(properties.getExpiryMinutes(), ChronoUnit.MINUTES))
                .attempts(0)
                .verified(false)
                .build());

        emailSender.send(normalized, subjectFor(purpose), bodyFor(recipientName, purpose, code));
    }

    /** Verifies the latest code for this email and purpose. Throws OtpException on any failure. */
    public void verify(String email, OtpPurpose purpose, String code) {
        String normalized = normalize(email);
        Instant now = Instant.now();

        Otp otp = otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalized, purpose)
                .orElseThrow(() -> new OtpException("Invalid or expired code."));

        if (otp.isVerified()) {
            throw new OtpException("Invalid or expired code.");
        }
        if (otp.getExpiresAt().isBefore(now)) {
            throw new OtpException("This code has expired. Please request a new one.");
        }

        // Claim an attempt atomically so parallel guesses cannot exceed the limit.
        Query claimQuery = Query.query(Criteria.where("id").is(otp.getId())
                .and("verified").is(false)
                .and("attempts").lt(properties.getMaxAttempts()));
        Otp claimed = mongoTemplate.findAndModify(
                claimQuery,
                new Update().inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                Otp.class);
        if (claimed == null) {
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please request a new code.");
        }

        if (!matches(claimed.getOtpHash(), hash(normalized, purpose, code == null ? "" : code.trim()))) {
            int remaining = properties.getMaxAttempts() - claimed.getAttempts();
            if (remaining <= 0) {
                throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many incorrect attempts. Please request a new code.");
            }
            throw new OtpException("Incorrect code. " + remaining + " attempt(s) remaining.");
        }

        long modified = mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(claimed.getId()).and("verified").is(false)),
                new Update().set("verified", true),
                Otp.class).getModifiedCount();
        if (modified != 1) {
            throw new OtpException("Invalid or expired code.");
        }
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String hash(String email, OtpPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((email + "|" + purpose.name() + "|" + code)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute OTP hash", ex);
        }
    }

    private boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> appName + ": verify your email";
            case PASSWORD_RESET -> appName + ": reset your password";
            case EMAIL_CHANGE -> appName + ": confirm your new email";
            case LOGIN -> appName + ": your login code";
        };
    }

    private String bodyFor(String recipientName, OtpPurpose purpose, String code) {
        String greeting = (recipientName == null || recipientName.isBlank()) ? "Hello," : "Hi " + recipientName + ",";
        String reason = switch (purpose) {
            case REGISTRATION -> "verify your email address";
            case PASSWORD_RESET -> "reset your password";
            case EMAIL_CHANGE -> "confirm your new email address";
            case LOGIN -> "log in";
        };
        return greeting + "\n\n"
                + "Use this code to " + reason + ":\n\n"
                + "    " + code + "\n\n"
                + "It expires in " + properties.getExpiryMinutes() + " minutes. "
                + "Never share this code with anyone.\n\n"
                + "If you didn't request it, you can safely ignore this email.\n\n"
                + "- The " + appName + " team";
    }
}
