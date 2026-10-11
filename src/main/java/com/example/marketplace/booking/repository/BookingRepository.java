package com.example.marketplace.booking.repository;

import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.enums.BookingStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookingRepository extends MongoRepository<Booking, String> {

    /** Ownership-scoped lookups: an id from a URL never reaches another user's booking. */
    Optional<Booking> findByIdAndCustomerId(String id, String customerId);

    Optional<Booking> findByIdAndWorkerId(String id, String workerId);

    Page<Booking> findByCustomerId(String customerId, Pageable pageable);

    Page<Booking> findByWorkerIdAndBookingStatusIn(String workerId, Collection<BookingStatus> statuses, Pageable pageable);

    long countByWorkerIdAndBookingStatusIn(String workerId, Collection<BookingStatus> statuses);

    /** Used to find the time ranges a worker already has booked on a date. */
    List<Booking> findByWorkerIdAndBookingDateAndBookingStatusIn(
            String workerId, String bookingDate, Collection<BookingStatus> statuses);
}
