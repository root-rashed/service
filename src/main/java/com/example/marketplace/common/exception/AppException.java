package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all expected (business) exceptions.
 * Carries the HTTP status that GlobalExceptionHandler should respond with.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AppException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
