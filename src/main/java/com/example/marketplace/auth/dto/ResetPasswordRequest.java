package com.example.marketplace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Password reset form. No @ToString on purpose: passwords must never reach logs. */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank(message = "Please enter the 6-digit code.")
    @Pattern(regexp = "^\\d{6}$", message = "The code must be 6 digits.")
    private String code;

    @NotBlank(message = "Please enter a new password.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
            message = "Password must be 8-72 characters and include a letter and a number.")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password.")
    private String confirmPassword;
}
