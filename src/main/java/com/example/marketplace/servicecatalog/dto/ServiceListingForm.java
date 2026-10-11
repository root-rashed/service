package com.example.marketplace.servicecatalog.dto;

import com.example.marketplace.common.enums.PriceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Worker input for creating or editing a service. The owner and status are never taken from here. */
@Getter
@Setter
public class ServiceListingForm {

    @NotBlank(message = "Please choose a category.")
    private String categoryId;

    @NotBlank(message = "Please enter a title.")
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters.")
    private String title;

    @NotBlank(message = "Please describe your service.")
    @Size(min = 30, max = 2000, message = "Description must be between 30 and 2000 characters.")
    private String description;

    /** One item per line, up to 10. */
    @Size(max = 1200, message = "The included-items list is too long.")
    private String includesText;

    @NotNull(message = "Please enter a price.")
    @DecimalMin(value = "1.00", message = "Price must be at least 1.")
    @DecimalMax(value = "1000000.00", message = "Price is too high.")
    @Digits(integer = 7, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal price;

    @NotNull(message = "Please choose a price type.")
    private PriceType priceType = PriceType.FIXED;

    @NotNull(message = "Please enter the duration in minutes.")
    @Min(value = 15, message = "Duration must be at least 15 minutes.")
    @Max(value = 720, message = "Duration cannot exceed 12 hours (720 minutes).")
    private Integer durationMinutes = 60;

    @NotBlank(message = "Please enter the area you serve.")
    @Size(max = 100, message = "Service area is too long.")
    private String serviceArea;

    @NotNull(message = "Please enter a service radius.")
    @Min(value = 1, message = "Radius must be at least 1 km.")
    @Max(value = 100, message = "Radius cannot exceed 100 km.")
    private Integer serviceRadiusKm = 10;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
    private Double longitude;
}
