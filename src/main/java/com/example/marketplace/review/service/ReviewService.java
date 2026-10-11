package com.example.marketplace.review.service;

import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.review.dto.ReviewDto;
import com.example.marketplace.review.dto.ReviewForm;
import com.example.marketplace.review.event.ReviewCreatedEvent;
import com.example.marketplace.review.model.Review;
import com.example.marketplace.review.repository.ReviewRepository;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.worker.model.WorkerProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Reviews and rating aggregates.
 *
 * Rules: only the customer of a COMPLETED booking can review it, once. The service, worker and customer
 * come from the stored booking, never from the request. Rating totals on the service and the worker
 * profile are updated with atomic $inc operations, and the stored average is only written when the
 * count it was based on is still current, so concurrent reviews can never leave a stale average.
 */
@Service
public class ReviewService {

    private static final int PAGE_SIZE = 10;

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewService(ReviewRepository reviewRepository,
                         BookingRepository bookingRepository,
                         MongoTemplate mongoTemplate,
                         ApplicationEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    // ==================== Write ====================

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.WORKER_PROFILES, allEntries = true)
    })
    public ReviewDto create(String customerId, String bookingId, ReviewForm form) {
        Booking booking = bookingRepository.findByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new ValidationException("You can review a booking once the job is completed.");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new ValidationException("You have already reviewed this booking.");
        }

        Review review = Review.builder()
                .bookingId(bookingId)
                .serviceId(booking.getServiceId())
                .workerId(booking.getWorkerId())
                .customerId(customerId)
                .serviceTitle(booking.getServiceTitle())
                .serviceSlug(booking.getServiceSlug())
                .authorName(displayName(booking.getCustomerName()))
                .rating(form.getRating())
                .comment(blankToNull(form.getComment()))
                .build();
        try {
            review = reviewRepository.save(review);
        } catch (DuplicateKeyException ex) {
            throw new ValidationException("You have already reviewed this booking.");
        }

        addToServiceRating(booking.getServiceId(), review.getRating());
        addToWorkerRating(booking.getWorkerId(), review.getRating());

        eventPublisher.publishEvent(new ReviewCreatedEvent(
                review.getId(), bookingId, booking.getWorkerId(), customerId, review.getRating()));
        return ReviewDto.from(review);
    }

    // ==================== Read ====================

    /** The customer's own review of a booking, or null if they haven't written one. */
    public ReviewDto findForBooking(String customerId, String bookingId) {
        return reviewRepository.findByBookingIdAndCustomerId(bookingId, customerId)
                .map(ReviewDto::from)
                .orElse(null);
    }

    public List<ReviewDto> getLatestForService(String serviceId, int limit) {
        return reviewRepository
                .findByServiceIdAndVisibleTrue(serviceId, newest(0, limit))
                .map(ReviewDto::from)
                .getContent();
    }

    public List<ReviewDto> getLatestForWorker(String workerUserId, int limit) {
        return reviewRepository
                .findByWorkerIdAndVisibleTrue(workerUserId, newest(0, limit))
                .map(ReviewDto::from)
                .getContent();
    }

    public Page<ReviewDto> getForWorker(String workerUserId, int page) {
        return reviewRepository
                .findByWorkerIdAndVisibleTrue(workerUserId, newest(Math.max(page, 0), PAGE_SIZE))
                .map(ReviewDto::from);
    }

    /** The reviews a customer has written, newest first, for their dashboard. */
    public List<ReviewDto> getLatestByCustomer(String customerId, int limit) {
        return reviewRepository
                .findByCustomerId(customerId, newest(0, limit))
                .map(ReviewDto::from)
                .getContent();
    }

    /** Latest written reviews for the homepage. Cached; cleared whenever a review is created. */
    @Cacheable(cacheNames = CacheNames.POPULAR_SERVICES, key = "'home-reviews:' + #root.args[0]")
    public List<ReviewDto> getLatestForHome(int limit) {
        return reviewRepository
                .findByVisibleTrueAndCommentIsNotNull(newest(0, Math.max(1, Math.min(limit, 12))))
                .map(ReviewDto::from)
                .getContent();
    }

    // ==================== Aggregates ====================

    private void addToServiceRating(String serviceId, int rating) {
        ServiceListing updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(serviceId)),
                new Update().inc("ratingSum", rating).inc("ratingCount", 1),
                FindAndModifyOptions.options().returnNew(true),
                ServiceListing.class);
        if (updated == null) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(serviceId).and("ratingCount").is(updated.getRatingCount())),
                new Update().set("ratingAverage", average(updated.getRatingSum(), updated.getRatingCount())),
                ServiceListing.class);
    }

    private void addToWorkerRating(String workerUserId, int rating) {
        WorkerProfile updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("userId").is(workerUserId)),
                new Update().inc("ratingSum", rating).inc("ratingCount", 1),
                FindAndModifyOptions.options().returnNew(true),
                WorkerProfile.class);
        if (updated == null) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(workerUserId).and("ratingCount").is(updated.getRatingCount())),
                new Update().set("ratingAverage", average(updated.getRatingSum(), updated.getRatingCount())),
                WorkerProfile.class);
    }

    static double average(int sum, int count) {
        if (count <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP).doubleValue();
    }

    // ==================== Helpers ====================

    private PageRequest newest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    /** "Farhana Khan" becomes "Farhana K."; a single name stays as is. */
    static String displayName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
