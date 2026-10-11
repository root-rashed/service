package com.example.marketplace.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Account details a user may edit. The email cannot be changed here. */
@Getter
@Setter
public class ProfileUpdateForm {

    @NotBlank(message = "Please enter your name.")
    @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters.")
    private String name;

    @NotBlank(message = "Please enter your phone number.")
    @Size(max = 25, message = "Phone number is too long.")
    private String phone;
}
