package com.example.marketplace.otp.model;

import com.example.marketplace.common.enums.OtpPurpose;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One-time passcode record. Only an HMAC hash of the code is stored, never the code itself.
 * Expired records are removed automatically by MongoDB one day after expiry (TTL index).
 */
@Document(collection = "otps")
@CompoundIndex(name = "email_purpose_created", def = "{'email': 1, 'purpose': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Otp {

    @Id
    private String id;

    /** Normalized (trimmed, lower-case) email the code was issued for. */
    private String email;

    private OtpPurpose purpose;

    private String otpHash;

    @Indexed(expireAfter = "1d")
    private Instant expiresAt;

    private int attempts;

    private boolean verified;

    private Instant createdAt;
}
