package com.example.marketplace.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** What the customer submits. The booking, service, worker and customer are resolved on the server. */
@Getter
@Setter
public class ReviewForm {

    @NotNull(message = "Please choose a rating.")
    @Min(value = 1, message = "Rating must be between 1 and 5.")
    @Max(value = 5, message = "Rating must be between 1 and 5.")
    private Integer rating;

    @Size(max = 1000, message = "Your comment can be at most 1000 characters.")
    private String comment;
}
