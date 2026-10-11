package com.example.marketplace.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Optional reason attached to a moderation decision.
 *
 * The same form serves profile reviews and service suspensions, because both do exactly one
 * thing: carry a short note that the affected user will read.
 */
@Getter
@Setter
public class ModerationForm {

    /** Optional. Shown to the professional/customer; never rendered back as markup. */
    @Size(max = 300, message = "Note must be at most 300 characters.")
    private String note;
}