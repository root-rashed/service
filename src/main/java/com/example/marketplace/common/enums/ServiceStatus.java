package com.example.marketplace.common.enums;

public enum ServiceStatus {
    ACTIVE,
    /** Deactivated by the owning worker. */
    INACTIVE,
    /** Taken down by an admin. */
    SUSPENDED;

    public boolean isBookable() {
        return this == ACTIVE;
    }
}
