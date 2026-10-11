package com.example.marketplace.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Block one day, or a range of days (vacation, time off). */
@Getter
@Setter
public class BlockedDateForm {

    @NotNull(message = "Please choose a start date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Optional; if empty only the start date is blocked. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    @Size(max = 100, message = "Reason is too long.")
    private String reason;
}
