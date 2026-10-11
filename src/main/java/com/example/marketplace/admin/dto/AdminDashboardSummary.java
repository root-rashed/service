package com.example.marketplace.admin.dto;

import com.example.marketplace.common.enums.BookingStatus;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import lombok.Value;

/**
 * Counts and money totals shown on the admin dashboard.
 * Money is always cash: collected, still owed, and the platform fee already earned.
 */
@Value
public class AdminDashboardSummary {

    long customers;
    long workers;
    long approvedWorkers;
    long pendingWorkers;
    long services;
    long activeServices;
    long bookings;
    long openBookings;
    long completedBookings;
    long reviews;
    long hiddenReviews;

    Map<BookingStatus, Long> bookingsByStatus;
    long unpaidCompletedBookings;
    BigDecimal cashCollected;
    BigDecimal cashOutstanding;
    BigDecimal platformFeesEarned;

    public static AdminDashboardSummary of(long customers, long workers, long approvedWorkers, long pendingWorkers,
                                           long services, long activeServices, long bookings, long completedBookings,
                                           long reviews, long hiddenReviews, Map<BookingStatus, Long> bookingsByStatus,
                                           long unpaidCompletedBookings, BigDecimal cashCollected,
                                           BigDecimal cashOutstanding, BigDecimal platformFeesEarned) {
        long open = bookingsByStatus.entrySet().stream()
                .filter(e -> e.getKey().blocksTimeSlot())
                .mapToLong(Map.Entry::getValue)
                .sum();
        return new AdminDashboardSummary(customers, workers, approvedWorkers, pendingWorkers,
                services, activeServices, bookings, open, completedBookings, reviews, hiddenReviews,
                new EnumMap<>(bookingsByStatus), unpaidCompletedBookings,
                cashCollected, cashOutstanding, platformFeesEarned);
    }

    /** Zero-filled map so the template can iterate it without null checks. */
    public Map<BookingStatus, Long> statuses() {
        Map<BookingStatus, Long> result = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            result.put(status, bookingsByStatus.getOrDefault(status, 0L));
        }
        return result;
    }

    public boolean hasOutstandingCash() {
        return unpaidCompletedBookings > 0;
    }

    public boolean hasPendingVerifications() {
        return pendingWorkers > 0;
    }
}