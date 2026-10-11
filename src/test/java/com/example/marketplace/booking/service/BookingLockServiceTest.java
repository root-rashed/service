package com.example.marketplace.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookingLockServiceTest {

    @Test
    void oneHourFromNineCoversTwelveCells() {
        int[] cells = BookingLockService.cellRange(9 * 60, 10 * 60);

        assertThat(cells[0]).isEqualTo(108); // 540 / 5
        assertThat(cells[1]).isEqualTo(119); // last cell is 595-599
        assertThat(cells[1] - cells[0] + 1).isEqualTo(12);
    }

    @Test
    void backToBackBookingsShareNoCells() {
        int[] first = BookingLockService.cellRange(9 * 60, 10 * 60);
        int[] second = BookingLockService.cellRange(10 * 60, 11 * 60);

        assertThat(second[0]).isGreaterThan(first[1]);
    }

    @Test
    void overlappingBookingsShareCells() {
        int[] first = BookingLockService.cellRange(9 * 60, 10 * 60);
        int[] second = BookingLockService.cellRange(9 * 60 + 30, 10 * 60 + 30);

        assertThat(second[0]).isLessThanOrEqualTo(first[1]);
    }

    @Test
    void oddMinutesAreRoundedOutwards() {
        int[] cells = BookingLockService.cellRange(9 * 60 + 7, 9 * 60 + 52);

        assertThat(cells[0]).isEqualTo(109); // 547 / 5 = 109 (starts in the 545-549 cell)
        assertThat(cells[1]).isEqualTo(118); // end 592 rounds up to the cell ending at 595
    }
}
