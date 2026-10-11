package com.example.marketplace.review.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer's review of a completed booking. One review per booking (unique bookingId index),
 * so duplicates are impossible even if the form is submitted twice at once.
 * All ids are set on the server from the booking, never from the browser.
 */
@Document(collection = "reviews")
@CompoundIndex(name = "worker_created", def = "{'workerId': 1, 'createdAt': -1}")
@CompoundIndex(name = "service_created", def = "{'serviceId': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    private String id;

    @Indexed(unique = true)
    private String bookingId;

    private String serviceId;
    private String workerId;
    private String customerId;

    // Snapshots for display
    private String serviceTitle;
    private String serviceSlug;
    /** Privacy-friendly name such as "Farhana K.". */
    private String authorName;

    /** 1 to 5. */
    private int rating;

    private String comment;

    /** False once an admin has removed the review. */
    @Builder.Default
    private boolean visible = true;

    @CreatedDate
    private Instant createdAt;
}
