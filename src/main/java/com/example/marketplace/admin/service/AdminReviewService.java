package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminReviewDto;
import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.review.model.Review;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Review moderation: hide a review that breaks the rules and publish it again if the
 * decision is reversed.
 *
 * Hiding does not delete the review or change any rating totals: the rating maths belongs to
 * ReviewService, and rewriting it here would drift from the aggregate it came from. Only the
 * derived caches are cleared, so hidden reviews stop showing immediately.
 */
@Service
public class AdminReviewService {

    private static final int PAGE_SIZE = 15;
    private static final int MAX_KEYWORD_LENGTH = 60;

    private final MongoTemplate mongoTemplate;

    public AdminReviewService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param visibility "visible", "hidden" or blank for all
     * @param query      optional free text matched against the service title and the author name
     */
    public Page<AdminReviewDto> search(String visibility, String query, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id")));
        Query mongoQuery = Query.query(buildCriteria(visibility, query));
        long total = mongoTemplate.count(mongoQuery, Review.class);
        mongoQuery.skip((long) pageable.getPageNumber() * PAGE_SIZE).limit(PAGE_SIZE);

        List<AdminReviewDto> content = mongoTemplate.find(mongoQuery, Review.class).stream()
                .map(AdminReviewDto::from)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    public long countVisible() {
        return mongoTemplate.count(Query.query(Criteria.where("visible").is(true)), Review.class);
    }

    public long countHidden() {
        return mongoTemplate.count(Query.query(Criteria.where("visible").is(false)), Review.class);
    }

    /** Removes a review from the public pages without deleting it. */
    @CacheEvict(cacheNames = {CacheNames.POPULAR_SERVICES, CacheNames.SERVICE_DETAILS, CacheNames.WORKER_PROFILES},
            allEntries = true)
    public void hide(String reviewId) {
        setVisible(reviewId, false);
    }

    /** Puts a hidden review back on the public pages. */
    @CacheEvict(cacheNames = {CacheNames.POPULAR_SERVICES, CacheNames.SERVICE_DETAILS, CacheNames.WORKER_PROFILES},
            allEntries = true)
    public void publish(String reviewId) {
        setVisible(reviewId, true);
    }

    // ---------- Internals ----------

    private void setVisible(String reviewId, boolean visible) {
        Review review = mongoTemplate.findById(reviewId, Review.class);
        if (review == null) {
            throw ResourceNotFoundException.of("Review");
        }
        if (review.isVisible() == visible) {
            throw new ValidationException(visible
                    ? "This review is already published."
                    : "This review is already hidden.");
        }
        review.setVisible(visible);
        mongoTemplate.save(review);
    }

    private Criteria buildCriteria(String visibility, String query) {
        List<Criteria> filters = new ArrayList<>(2);
        String flag = visibility == null ? "" : visibility.trim().toLowerCase(Locale.ROOT);
        if ("visible".equals(flag)) {
            filters.add(Criteria.where("visible").is(true));
        } else if ("hidden".equals(flag)) {
            filters.add(Criteria.where("visible").is(false));
        }
        String term = query == null ? "" : query.trim();
        if (term.length() > MAX_KEYWORD_LENGTH) {
            term = term.substring(0, MAX_KEYWORD_LENGTH);
        }
        if (!term.isEmpty()) {
            // Pattern.quote keeps user input out of the regex engine entirely.
            String regex = Pattern.quote(term);
            filters.add(new Criteria().orOperator(
                    Criteria.where("serviceTitle").regex(regex, "i"),
                    Criteria.where("authorName").regex(regex, "i"),
                    Criteria.where("comment").regex(regex, "i")));
        }
        if (filters.isEmpty()) {
            return new Criteria();
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new Criteria().andOperator(filters.toArray(new Criteria[0]));
    }
}