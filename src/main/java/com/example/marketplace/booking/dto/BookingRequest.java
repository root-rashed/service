package com.example.marketplace.booking.dto;

import com.example.marketplace.common.enums.PaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * What the customer chooses when booking. Everything else (customer, worker, price, fee, total,
 * payment status, booking status) is decided by the server and is never read from the browser.
 */
@Getter
@Setter
public class BookingRequest {

    @NotBlank(message = "Please choose a service.")
    private String serviceSlug;

    @NotNull(message = "Please choose a date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    /** Start time in minutes from midnight, taken from a slot returned by the availability API. */
    @NotNull(message = "Please choose a time.")
    @Min(value = 0, message = "Invalid time.")
    @Max(value = 1439, message = "Invalid time.")
    private Integer startMinute;

    @NotBlank(message = "Please enter the address for the service.")
    @Size(min = 10, max = 250, message = "Address must be between 10 and 250 characters.")
    private String address;

    @Size(max = 500, message = "The note can be at most 500 characters.")
    private String customerNote;

    /** CASH is the only payment method. */
    @NotNull(message = "Please choose a payment method.")
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
}
