package com.example.marketplace.notification.service;

import com.example.marketplace.admin.event.WorkerModerationEvent;
import com.example.marketplace.common.enums.NotificationType;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells a professional what an administrator decided about their profile.
 *
 * The recipient comes from the event (server-side), the link is a fixed in-app path, and
 * the failure of a notification never rolls back the moderation decision itself.
 */
@Slf4j
@Component
public class WorkerModerationNotificationListener {

    private final NotificationService notificationService;

    public WorkerModerationNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onWorkerModerated(WorkerModerationEvent event) {
        NotificationType type = typeFor(event.status());
        if (type == null) {
            return;
        }
        String message = switch (event.status()) {
            case APPROVED -> "Your professional profile is live. Customers can find and book you now.";
            case REJECTED -> "Your professional profile was not approved."
                    + (hasNote(event) ? " " + event.note().trim() : "")
                    + " Update your details and ask us to review it again.";
            case SUSPENDED -> "Your professional profile and services are suspended and are no longer visible."
                    + (hasNote(event) ? " " + event.note().trim() : "");
            case PENDING -> "Your professional profile is back under review. It will be visible again once approved.";
        };
        notificationService.notify(event.workerUserId(), type, type.getDefaultTitle(), message, "/worker/profile");
    }

    private NotificationType typeFor(WorkerVerificationStatus status) {
        return switch (status) {
            case APPROVED -> NotificationType.WORKER_APPROVED;
            case REJECTED -> NotificationType.WORKER_REJECTED;
            case SUSPENDED -> NotificationType.WORKER_SUSPENDED;
            case PENDING -> NotificationType.WORKER_RESTORED;
        };
    }

    private boolean hasNote(WorkerModerationEvent event) {
        return event.note() != null && !event.note().isBlank();
    }
}