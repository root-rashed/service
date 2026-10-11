package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    /** Builds a message like "Service not found" without echoing identifiers. */
    public static ResourceNotFoundException of(String resource) {
        return new ResourceNotFoundException(resource + " not found");
    }
}
