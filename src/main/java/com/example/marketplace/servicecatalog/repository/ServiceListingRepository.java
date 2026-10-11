package com.example.marketplace.servicecatalog.repository;

import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ServiceListingRepository extends MongoRepository<ServiceListing, String> {

    List<ServiceListing> findByWorkerIdOrderByCreatedAtDesc(String workerId);

    /** Ownership-scoped lookup: a worker can never load another worker's service by id. */
    Optional<ServiceListing> findByIdAndWorkerId(String id, String workerId);

    long countByWorkerId(String workerId);

    /** Public detail lookup: ACTIVE service of an approved worker. */
    Optional<ServiceListing> findBySlugAndStatusAndWorkerApprovedTrue(String slug, ServiceStatus status);

    /** Public "popular" lookup, restricted to the given (active) categories. */
    List<ServiceListing> findByStatusAndWorkerApprovedTrueAndCategoryIdIn(
            ServiceStatus status, Collection<String> categoryIds, Pageable pageable);

    boolean existsBySlug(String slug);
}
