package com.example.marketplace.admin.event;

import com.example.marketplace.common.enums.WorkerVerificationStatus;

/**
 * Published when an administrator makes a moderation decision about a worker profile.
 *
 * Distinct from {@code WorkerVerificationChangedEvent} (which also fires when a worker edits
 * their own profile), so only real moderation decisions generate a message to the worker.
 *
 * @param workerUserId the affected worker's user id
 * @param status       the status the profile was moved to
 * @param note         optional reason shown to the worker
 */
public record WorkerModerationEvent(
        String workerUserId,
        WorkerVerificationStatus status,
        String note
) {
}