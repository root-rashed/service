package com.example.marketplace.schedule.dto;

/**
 * A half-open time range inside one day, in minutes from midnight: [startMinute, endMinute).
 * Back-to-back ranges (one ending when the next starts) do not overlap.
 */
public record TimeRange(int startMinute, int endMinute) {

    public boolean overlaps(TimeRange other) {
        return startMinute < other.endMinute && other.startMinute < endMinute;
    }
}
