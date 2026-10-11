package com.example.marketplace.schedule.dto;

import lombok.Value;

/** A bookable start time for a service, with display labels. */
@Value
public class TimeSlot {

    int startMinute;
    int endMinute;
    /** 12-hour label, e.g. "9:00 AM". */
    String startLabel;
    String endLabel;
    /** 24-hour value, e.g. "09:00", handy for form fields. */
    String start24;

    public static TimeSlot of(TimeRange range) {
        return new TimeSlot(
                range.startMinute(),
                range.endMinute(),
                format12(range.startMinute()),
                format12(range.endMinute()),
                format24(range.startMinute()));
    }

    public static String format24(int minuteOfDay) {
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    public static String format12(int minuteOfDay) {
        int hour24 = (minuteOfDay / 60) % 24;
        int minute = minuteOfDay % 60;
        int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        return hour12 + ":" + String.format("%02d", minute) + (hour24 < 12 ? " AM" : " PM");
    }
}
