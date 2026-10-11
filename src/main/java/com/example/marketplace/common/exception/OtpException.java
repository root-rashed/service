package com.example.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/** OTP failures: invalid, expired, too many attempts, resend cooldown. */
public class OtpException extends AppException {

    public OtpException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public OtpException(HttpStatus status, String message) {
        super(status, message);
    }
}
