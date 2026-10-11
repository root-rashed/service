package com.example.marketplace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Registration email verification form. */
@Getter
@Setter
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank(message = "Please enter the 6-digit code.")
    @Pattern(regexp = "^\\d{6}$", message = "The code must be 6 digits.")
    private String code;
}
