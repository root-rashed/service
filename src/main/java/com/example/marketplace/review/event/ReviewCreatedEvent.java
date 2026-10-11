package com.example.marketplace.review.event;

/** Published after a review is saved; the notification module can tell the worker. */
public record ReviewCreatedEvent(String reviewId, String bookingId, String workerId, String customerId, int rating) {
}
