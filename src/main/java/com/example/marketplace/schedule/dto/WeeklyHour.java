package com.example.marketplace.schedule.dto;

import lombok.Value;

/** One line of the public "working hours" table. */
@Value
public class WeeklyHour {

    String dayLabel;
    boolean working;
    /** e.g. "9:00 AM - 5:00 PM", or "Closed". */
    String hoursLabel;
}
