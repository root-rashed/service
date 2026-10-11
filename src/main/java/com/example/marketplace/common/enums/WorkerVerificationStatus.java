package com.example.marketplace.common.enums;

/** Admin-controlled verification state of a worker profile. */
public enum WorkerVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    public boolean isPubliclyVisible() {
        return this == APPROVED;
    }
}
