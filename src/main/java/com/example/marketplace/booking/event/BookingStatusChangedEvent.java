package com.example.marketplace.booking.event;

import com.example.marketplace.common.enums.BookingStatus;

/**
 * Published after every booking status change (and once when a booking is created).
 * The notification module listens to send emails and in-app messages.
 *
 * @param from the previous status, or null when the booking was just created
 */
public record BookingStatusChangedEvent(String bookingId, String customerId, String workerId,
                                        BookingStatus from, BookingStatus to) {
}
