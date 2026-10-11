package com.example.marketplace.worker.event;

import com.example.marketplace.common.enums.WorkerVerificationStatus;

/**
 * Published whenever a worker profile is saved or its verification status changes.
 * Other modules (e.g. the service catalog) listen to keep their own data and caches in sync
 * without depending on the worker module's internals.
 *
 * @param userId the worker's user id
 * @param status the worker's current verification status
 */
public record WorkerVerificationChangedEvent(String userId, WorkerVerificationStatus status) {
}
