package com.example.marketplace.common.enums;

/**
 * Kind of in-app message. The icon and colour shown in the UI are derived from this,
 * so notification content never carries presentation data.
 */
public enum NotificationType {
    BOOKING_REQUEST("bi-bell-fill", "amber", "New booking request"),
    BOOKING_ACCEPTED("bi-check-circle-fill", "green", "Booking confirmed"),
    BOOKING_REJECTED("bi-x-circle-fill", "red", "Booking declined"),
    BOOKING_CANCELLED("bi-slash-circle-fill", "red", "Booking cancelled"),
    BOOKING_IN_PROGRESS("bi-tools", "blue", "Job in progress"),
    BOOKING_COMPLETED("bi-check2-all", "green", "Job completed"),
    CASH_PAID("bi-cash-coin", "green", "Cash payment recorded"),
    REVIEW_RECEIVED("bi-star-fill", "orange", "New review"),
    WORKER_APPROVED("bi-patch-check-fill", "green", "Profile approved"),
    WORKER_REJECTED("bi-x-circle-fill", "red", "Profile not approved"),
    WORKER_SUSPENDED("bi-slash-circle-fill", "red", "Profile suspended"),
    WORKER_RESTORED("bi-arrow-counterclockwise", "blue", "Profile under review");

    private final String icon;
    private final String tone;
    private final String defaultTitle;

    NotificationType(String icon, String tone, String defaultTitle) {
        this.icon = icon;
        this.tone = tone;
        this.defaultTitle = defaultTitle;
    }

    public String getIcon() {
        return icon;
    }

    /** Badge colour key used by the design system (amber, green, red, blue, orange). */
    public String getTone() {
        return tone;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }
}
