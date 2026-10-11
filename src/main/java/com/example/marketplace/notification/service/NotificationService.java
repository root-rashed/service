package com.example.marketplace.notification.service;

import com.example.marketplace.common.enums.NotificationType;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.notification.dto.NotificationDto;
import com.example.marketplace.notification.model.Notification;
import com.example.marketplace.notification.repository.NotificationRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * In-app notifications.
 *
 * The recipient is always an explicit user id supplied by the module that knows who the
 * message is for; nothing is ever derived from the browser. Reads and updates are scoped by
 * userId, so a notification id taken from a URL cannot touch somebody else's message.
 * Notifications are per-user, read-mutating and therefore never cached.
 */
@Slf4j
@Service
public class NotificationService {

    private static final int PAGE_SIZE = 15;
    /** Guards against a pathological event storm filling one account's list. */
    private static final int MAX_PER_RECIPIENT = 500;

    private final NotificationRepository repository;
    private final MongoTemplate mongoTemplate;

    public NotificationService(NotificationRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates a message for one recipient. Never throws: a failed notification must not roll back the business action. */
    public void notify(String userId, NotificationType type, String title, String message, String link) {
        if (userId == null || userId.isBlank() || type == null) {
            return;
        }
        try {
            repository.save(Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title == null || title.isBlank() ? type.getDefaultTitle() : title)
                    .message(trim(message))
                    .link(normalizeLink(link))
                    .read(false)
                    .build());
            trimOldest(userId);
        } catch (RuntimeException ex) {
            log.warn("Could not store notification for {}: {}", type, ex.getClass().getSimpleName());
        }
    }

    public Page<NotificationDto> list(String userId, int page) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(NotificationDto::from);
    }

    /** Up to five unread messages for the header dropdown. */
    public List<NotificationDto> recentUnread(String userId) {
        return repository.findTop5ByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::from)
                .toList();
    }

    /**
     * The newest messages regardless of whether they have been read, for the dashboard. Always a
     * fresh read: the count and the list change as bookings move through their states.
     */
    public List<NotificationDto> recent(String userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(limit, 1)))
                .map(NotificationDto::from)
                .getContent();
    }

    public long unreadCount(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    /** Marks one message as read. Ownership is part of the query, so a foreign id is a 404. */
    public void markRead(String userId, String notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification"));
        if (!notification.isRead()) {
            notification.setRead(true);
            repository.save(notification);
        }
    }

    public void markAllRead(String userId) {
        // One scoped update instead of loading and re-saving every document.
        mongoTemplate.updateMulti(Query.query(Criteria.where("userId").is(userId).and("read").is(false)),
                new Update().set("read", true), Notification.class);
    }

    // ---------- Helpers ----------

    /** Keeps only the newest MAX_PER_RECIPIENT messages per recipient. */
    private void trimOldest(String userId) {
        List<Notification> all = repository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(0, MAX_PER_RECIPIENT + 1)).getContent();
        if (all.size() > MAX_PER_RECIPIENT) {
            repository.deleteAll(all.subList(MAX_PER_RECIPIENT, all.size()));
        }
    }

    /** Links must stay inside this application; anything else is dropped rather than rendered. */
    private String normalizeLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String trimmed = link.trim();
        return (trimmed.startsWith("/") && !trimmed.startsWith("//")) ? trimmed : null;
    }

    private String trim(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }
}
