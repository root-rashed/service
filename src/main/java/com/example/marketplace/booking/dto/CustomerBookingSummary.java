package com.example.marketplace.booking.dto;

import java.util.List;
import lombok.Value;

/** Booking snippets for the customer dashboard. */
@Value
public class CustomerBookingSummary {

    /** The soonest active booking among the customer's latest bookings, or null. */
    BookingDto upcoming;
    /** The three most recent bookings. */
    List<BookingDto> recent;
}
