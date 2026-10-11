package com.example.marketplace.common.enums;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    WORKER_ACCEPTED,
    WORKER_REJECTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public Set<BookingStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(WORKER_ACCEPTED, WORKER_REJECTED, CANCELLED);
            case WORKER_ACCEPTED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED);
            case WORKER_REJECTED, COMPLETED, CANCELLED -> EnumSet.noneOf(BookingStatus.class);
        };
    }

    public boolean canTransitionTo(BookingStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /** True while the booking occupies the worker's time slot (used for overlap checks). */
    public boolean blocksTimeSlot() {
        return this == PENDING || this == WORKER_ACCEPTED || this == IN_PROGRESS;
    }

    /** Customers may cancel only before work starts. */
    public boolean isCancellableByCustomer() {
        return canTransitionTo(CANCELLED);
    }
}
