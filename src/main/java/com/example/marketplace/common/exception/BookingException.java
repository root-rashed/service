package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/** Booking rule violations: slot conflicts, invalid state transitions, etc. */
public class BookingException extends AppException {

    public BookingException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
