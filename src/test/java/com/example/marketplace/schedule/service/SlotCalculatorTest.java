package com.example.marketplace.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.schedule.dto.TimeRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotCalculatorTest {

    private static final int NINE = 9 * 60;
    private static final int FIVE_PM = 17 * 60;

    @Test
    void fullDayWithOneHourServiceGivesHalfHourStarts() {
        List<TimeRange> slots = SlotCalculator.compute(NINE, FIVE_PM, 60, 30, 0, List.of());

        assertThat(slots).hasSize(15); // 09:00 ... 16:00
        assertThat(slots.get(0)).isEqualTo(new TimeRange(NINE, NINE + 60));
        assertThat(slots.get(slots.size() - 1)).isEqualTo(new TimeRange(16 * 60, FIVE_PM));
    }

    @Test
    void serviceLongerThanWorkingDayGivesNoSlots() {
        assertThat(SlotCalculator.compute(NINE, FIVE_PM, 9 * 60, 30, 0, List.of())).isEmpty();
    }

    @Test
    void existingBookingBlocksOverlappingSlotsButNotBackToBackOnes() {
        TimeRange booked = new TimeRange(11 * 60, 12 * 60); // 11:00 - 12:00
        List<TimeRange> slots = SlotCalculator.compute(NINE, FIVE_PM, 60, 30, 0, List.of(booked));

        List<Integer> starts = slots.stream().map(TimeRange::startMinute).toList();
        assertThat(starts).contains(10 * 60);          // 10:00-11:00 ends exactly when the booking starts
        assertThat(starts).doesNotContain(10 * 60 + 30); // 10:30-11:30 overlaps
        assertThat(starts).doesNotContain(11 * 60);    // 11:00-12:00 is taken
        assertThat(starts).doesNotContain(11 * 60 + 30); // 11:30-12:30 overlaps
        assertThat(starts).contains(12 * 60);          // 12:00 is free again
    }

    @Test
    void earliestStartRemovesPastSlots() {
        List<TimeRange> slots = SlotCalculator.compute(NINE, FIVE_PM, 60, 30, 13 * 60 + 10, List.of());

        assertThat(slots.get(0).startMinute()).isEqualTo(13 * 60 + 30);
    }

    @Test
    void invalidInputGivesNoSlots() {
        assertThat(SlotCalculator.compute(FIVE_PM, NINE, 60, 30, 0, List.of())).isEmpty();
        assertThat(SlotCalculator.compute(NINE, FIVE_PM, 0, 30, 0, List.of())).isEmpty();
        assertThat(SlotCalculator.compute(NINE, FIVE_PM, 60, 0, 0, List.of())).isEmpty();
    }

    @Test
    void overlapRuleIsHalfOpen() {
        assertThat(new TimeRange(60, 120).overlaps(new TimeRange(120, 180))).isFalse();
        assertThat(new TimeRange(60, 120).overlaps(new TimeRange(119, 180))).isTrue();
        assertThat(new TimeRange(60, 180).overlaps(new TimeRange(90, 100))).isTrue();
    }
}
