package com.example.marketplace.review.repository;

import com.example.marketplace.review.model.Review;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewRepository extends MongoRepository<Review, String> {

    boolean existsByBookingId(String bookingId);

    Optional<Review> findByBookingIdAndCustomerId(String bookingId, String customerId);

    Page<Review> findByServiceIdAndVisibleTrue(String serviceId, Pageable pageable);

    Page<Review> findByWorkerIdAndVisibleTrue(String workerId, Pageable pageable);

    /**
     * Everything a customer has written, newest first. Includes reviews a moderator has hidden,
     * because a customer should still see their own words.
     */
    Page<Review> findByCustomerId(String customerId, Pageable pageable);

    /** Latest written reviews for the homepage. */
    Page<Review> findByVisibleTrueAndCommentIsNotNull(Pageable pageable);
}
