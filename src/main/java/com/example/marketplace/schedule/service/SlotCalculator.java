package com.example.marketplace.schedule.service;

import com.example.marketplace.schedule.dto.TimeRange;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure slot maths with no Spring or database dependencies, so it is easy to unit test.
 * A slot is available when it fits inside the working hours, starts no earlier than the earliest
 * allowed time, and does not overlap any busy range.
 */
public final class SlotCalculator {

    private SlotCalculator() {
    }

    /**
     * @param dayStart      start of working hours (minutes from midnight)
     * @param dayEnd        end of working hours
     * @param durationMin   length of the service
     * @param stepMin       distance between slot start times, counted from dayStart
     * @param earliestStart no slot may start before this minute (used for "today" and minimum notice)
     * @param busy          ranges already taken (existing bookings)
     */
    public static List<TimeRange> compute(int dayStart, int dayEnd, int durationMin, int stepMin,
                                          int earliestStart, List<TimeRange> busy) {
        List<TimeRange> slots = new ArrayList<>();
        if (durationMin <= 0 || stepMin <= 0 || dayEnd <= dayStart) {
            return slots;
        }
        for (int start = dayStart; start + durationMin <= dayEnd; start += stepMin) {
            if (start < earliestStart) {
                continue;
            }
            TimeRange candidate = new TimeRange(start, start + durationMin);
            boolean taken = busy.stream().anyMatch(candidate::overlaps);
            if (!taken) {
                slots.add(candidate);
            }
        }
        return slots;
    }
}
