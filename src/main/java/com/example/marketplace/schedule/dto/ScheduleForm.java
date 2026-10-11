package com.example.marketplace.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

/** Weekly working-hours form: exactly 7 rows, Monday to Sunday. */
@Getter
@Setter
public class ScheduleForm {

    @Valid
    @Size(min = 7, max = 7, message = "The schedule must contain all 7 days.")
    private List<DayForm> days = new ArrayList<>();

    @Getter
    @Setter
    public static class DayForm {

        @NotBlank
        private String dayOfWeek;

        private boolean working;

        /** 24-hour "HH:mm" (what an HTML time input submits). */
        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use a time like 09:00.")
        private String startTime;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use a time like 17:00.")
        private String endTime;

        public String getDayLabel() {
            try {
                return DayOfWeek.valueOf(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            } catch (RuntimeException ex) {
                return "";
            }
        }
    }
}
