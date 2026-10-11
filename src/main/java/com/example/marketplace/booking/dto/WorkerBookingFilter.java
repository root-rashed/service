package com.example.marketplace.booking.dto;

import com.example.marketplace.common.enums.BookingStatus;
import java.util.Set;

/** Tabs on the worker's bookings page. */
public enum WorkerBookingFilter {
    PENDING("Requests", Set.of(BookingStatus.PENDING)),
    UPCOMING("Upcoming", Set.of(BookingStatus.WORKER_ACCEPTED, BookingStatus.IN_PROGRESS)),
    HISTORY("History", Set.of(BookingStatus.COMPLETED, BookingStatus.WORKER_REJECTED, BookingStatus.CANCELLED));

    private final String label;
    private final Set<BookingStatus> statuses;

    WorkerBookingFilter(String label, Set<BookingStatus> statuses) {
        this.label = label;
        this.statuses = statuses;
    }

    public String getLabel() {
        return label;
    }

    public Set<BookingStatus> getStatuses() {
        return statuses;
    }
}
