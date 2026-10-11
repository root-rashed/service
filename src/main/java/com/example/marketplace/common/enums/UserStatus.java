package com.example.marketplace.common.enums;

public enum UserStatus {
    /** Registered but email OTP not yet verified. */
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED;

    public boolean canLogin() {
        return this == ACTIVE;
    }
}
