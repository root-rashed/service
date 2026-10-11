package com.example.marketplace.common.exception;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Business-level validation failure, optionally with per-field messages. */
public class ValidationException extends AppException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Collections.emptyMap());
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
