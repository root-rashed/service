package com.example.marketplace.booking.event;

/** Published when a booking's cash payment is marked as PAID. */
public record CashPaymentConfirmedEvent(String bookingId, String customerId, String workerId) {
}
