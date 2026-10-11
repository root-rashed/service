package com.example.marketplace.booking.service;

import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.schedule.dto.TimeRange;
import com.example.marketplace.schedule.service.BusyTimeSource;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Tells the availability calculation which times are already taken by active bookings
 * (PENDING, WORKER_ACCEPTED, IN_PROGRESS). Always reads live data.
 */
@Component
public class BookingBusyTimeSource implements BusyTimeSource {

    private static final Set<BookingStatus> ACTIVE = Arrays.stream(BookingStatus.values())
            .filter(BookingStatus::blocksTimeSlot)
            .collect(Collectors.toUnmodifiableSet());

    private final BookingRepository bookingRepository;

    public BookingBusyTimeSource(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public List<TimeRange> busyRanges(String workerUserId, LocalDate date) {
        return bookingRepository
                .findByWorkerIdAndBookingDateAndBookingStatusIn(workerUserId, date.toString(), ACTIVE).stream()
                .map(b -> new TimeRange(b.getStartMinute(), b.getEndMinute()))
                .toList();
    }
}
