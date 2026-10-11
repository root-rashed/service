package com.example.marketplace.notification.model;

import com.example.marketplace.common.enums.NotificationType;
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
 * One in-app message for a single account.
 *
 * userId is always taken from the event that produced it, never from the request, so a
 * notification can only ever be created for a real recipient. Notification state is personal
 * and time-ordered, so it is never cached.
 */
@Document(collection = "notifications")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
@CompoundIndex(name = "user_unread", def = "{'userId': 1, 'read': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    /** Recipient's user id. */
    @Indexed
    private String userId;

    private NotificationType type;

    private String title;

    private String message;

    /** In-app link to the related record, e.g. /booking/{id}. Relative only. */
    private String link;

    /** Set on booking-related messages so a page can deep-link to it. */
    private String bookingId;

    @Builder.Default
    private boolean read = false;

    @CreatedDate
    private Instant createdAt;
}
