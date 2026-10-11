package com.example.marketplace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Registration form. No @ToString on purpose: passwords must never reach logs. */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Please enter your name.")
    @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters.")
    private String name;

    @NotBlank(message = "Please enter your email.")
    @Email(message = "Please enter a valid email address.")
    @Size(max = 254, message = "Email is too long.")
    private String email;

    @NotBlank(message = "Please enter your phone number.")
    @Size(max = 25, message = "Phone number is too long.")
    private String phone;

    @NotBlank(message = "Please enter a password.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
            message = "Password must be 8-72 characters and include a letter and a number.")
    private String password;

    @NotBlank(message = "Please confirm your password.")
    private String confirmPassword;

    @NotNull(message = "Please choose an account type.")
    private AccountType accountType = AccountType.CUSTOMER;
}
