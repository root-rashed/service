package com.example.marketplace.booking.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One 5-minute cell of a worker's day that is taken by a booking.
 * The id is "workerId:date:cell", so MongoDB's unique _id guarantees that two bookings can never
 * hold the same cell, even if requests arrive at the same millisecond (no transactions needed).
 * Locks expire automatically at the end of the booking date.
 */
@Document(collection = "booking_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingLock {

    @Id
    private String id;

    @Indexed
    private String bookingId;

    @Indexed(expireAfter = "0s")
    private Instant expireAt;
}
