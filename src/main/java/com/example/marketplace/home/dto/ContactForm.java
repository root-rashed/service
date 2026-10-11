package com.example.marketplace.home.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Public contact form. Only the fields a visitor may supply; anything the platform
 * needs (recipient, timestamps, sender address) is added server-side.
 */
@Getter
@Setter
public class ContactForm {

    @NotBlank(message = "Please enter your name.")
    @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters.")
    private String name;

    @NotBlank(message = "Please enter your email.")
    @Email(message = "Please enter a valid email address.")
    @Size(max = 254, message = "Email is too long.")
    private String email;

    /** Optional: only used so support can call back if a reply is needed. */
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Please enter a valid phone number.")
    private String phone;

    @NotBlank(message = "Please enter a subject.")
    @Size(max = 120, message = "Subject must be at most 120 characters.")
    private String subject;

    @NotBlank(message = "Please enter a message.")
    @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters.")
    private String message;
}