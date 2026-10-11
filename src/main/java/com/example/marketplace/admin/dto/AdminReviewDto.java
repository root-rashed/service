package com.example.marketplace.admin.dto;

import com.example.marketplace.review.model.Review;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Value;

/** Review moderation view: the public review fields plus the moderation flag. */
@Value
public class AdminReviewDto {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    String bookingId;
    String serviceTitle;
    String author;
    int rating;
    String comment;
    boolean visible;
    String createdOn;

    public static AdminReviewDto from(Review r) {
        return new AdminReviewDto(
                r.getId(),
                r.getBookingId(),
                r.getServiceTitle(),
                r.getAuthorName(),
                r.getRating(),
                r.getComment(),
                r.isVisible(),
                r.getCreatedAt() == null ? "" : DATE.format(r.getCreatedAt()));
    }

    public String visibilityLabel() {
        return visible ? "Published" : "Hidden";
    }

    public String visibilityTone() {
        return visible ? "green" : "red";
    }
}