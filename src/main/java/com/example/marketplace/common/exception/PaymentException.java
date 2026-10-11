package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/** Cash payment rule violations (e.g. marking an already-paid booking as paid). */
public class PaymentException extends AppException {

    public PaymentException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
