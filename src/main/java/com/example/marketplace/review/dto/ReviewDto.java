package com.example.marketplace.review.dto;

import com.example.marketplace.review.model.Review;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Value;

/**
 * Read-only review view. Property names (author, serviceTitle, rating, comment) match the shared
 * reviewCard fragment, so the same card is used on the homepage, service pages and worker pages.
 */
@Value
public class ReviewDto {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    String author;
    String serviceTitle;
    String serviceSlug;
    int rating;
    String comment;
    String dateLabel;

    public static ReviewDto from(Review r) {
        return new ReviewDto(
                r.getId(),
                r.getAuthorName(),
                r.getServiceTitle(),
                r.getServiceSlug(),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt() == null ? "" : DATE.format(r.getCreatedAt()));
    }
}
