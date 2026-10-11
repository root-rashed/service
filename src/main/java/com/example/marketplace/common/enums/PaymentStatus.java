package com.example.marketplace.common.enums;

/**
 * Cash payment state, kept separate from the booking state.
 * PENDING -> PAID (cash collected) or PENDING -> CANCELLED.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    CANCELLED;

    public boolean canTransitionTo(PaymentStatus next) {
        return this == PENDING && (next == PAID || next == CANCELLED);
    }

    public boolean isFinal() {
        return this != PENDING;
    }
}
