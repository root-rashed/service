package com.example.marketplace.otp.repository;

import com.example.marketplace.common.enums.OtpPurpose;
import com.example.marketplace.otp.model.Otp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OtpRepository extends MongoRepository<Otp, String> {

    Optional<Otp> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    long countByEmailAndPurposeAndCreatedAtAfter(String email, OtpPurpose purpose, Instant after);
}
