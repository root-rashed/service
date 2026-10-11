package com.example.marketplace.worker.repository;

import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.worker.model.WorkerProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WorkerProfileRepository extends MongoRepository<WorkerProfile, String> {

    Optional<WorkerProfile> findByUserId(String userId);

    Optional<WorkerProfile> findByUserIdAndVerificationStatus(String userId, WorkerVerificationStatus status);

    Optional<WorkerProfile> findBySlugAndVerificationStatus(String slug, WorkerVerificationStatus status);

    Page<WorkerProfile> findByVerificationStatus(WorkerVerificationStatus status, Pageable pageable);

    boolean existsBySlug(String slug);
}
