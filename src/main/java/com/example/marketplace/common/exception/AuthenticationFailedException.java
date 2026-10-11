package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level authentication failure (HTTP 401).
 * Named to avoid clashing with Spring Security's AuthenticationException.
 */
public class AuthenticationFailedException extends AppException {

    public AuthenticationFailedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
