package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level authorization/ownership failure (HTTP 403).
 * Named to avoid clashing with Spring Security's AccessDeniedException.
 */
public class AccessForbiddenException extends AppException {

    public AccessForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }

    public AccessForbiddenException() {
        this("You do not have permission to perform this action.");
    }
}
