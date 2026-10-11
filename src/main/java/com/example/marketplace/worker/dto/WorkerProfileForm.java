package com.example.marketplace.worker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Form for creating or editing the logged-in worker's own profile. */
@Getter
@Setter
public class WorkerProfileForm {

    @NotBlank(message = "Please enter your profession, for example Plumber.")
    @Size(min = 2, max = 80, message = "Profession must be between 2 and 80 characters.")
    private String headline;

    @NotBlank(message = "Please tell customers about yourself.")
    @Size(min = 30, max = 1000, message = "Your bio must be between 30 and 1000 characters.")
    private String bio;

    @NotNull(message = "Please enter your years of experience.")
    @Min(value = 0, message = "Experience cannot be negative.")
    @Max(value = 60, message = "Experience cannot exceed 60 years.")
    private Integer experienceYears;

    /** Comma-separated, up to 10 skills. */
    @Size(max = 400, message = "Skills list is too long.")
    private String skillsText;

    @NotBlank(message = "Please enter your main area, for example Mirpur.")
    @Size(min = 2, max = 60, message = "Area must be between 2 and 60 characters.")
    private String area;

    @Size(max = 200, message = "Service area description is too long.")
    private String serviceArea;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
    private Double longitude;
}
