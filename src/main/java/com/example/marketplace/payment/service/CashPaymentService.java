package com.example.marketplace.payment.service;

import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.exception.PaymentException;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.booking.event.CashPaymentConfirmedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Cash payment rules, kept separate from the booking state machine.
 * There is no online payment: the customer pays the worker in cash after the job, and the worker
 * (or later an admin) then marks the booking's payment as PAID. Payment state is never cached.
 */
@Service
public class CashPaymentService {

    private final BookingRepository bookingRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CashPaymentService(BookingRepository bookingRepository,
                              MongoTemplate mongoTemplate,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.bookingRepository = bookingRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** The worker confirms they received the cash for a completed job. */
    public void markPaidByWorker(String workerId, String bookingId) {
        Booking booking = bookingRepository.findByIdAndWorkerId(bookingId, workerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        markPaid(booking, Criteria.where("workerId").is(workerId));
    }

    /**
     * Administrator correction, for example when a professional collected the cash but never
     * confirmed it. No ownership scope is applied here on purpose, but the same booking-state
     * and payment-state guards and the same conditional update still apply.
     */
    public void markPaidByAdmin(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        markPaid(booking, Criteria.where("id").is(bookingId));
    }

    private void markPaid(Booking booking, Criteria ownership) {
        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new PaymentException("Cash can only be marked as received after the job is completed.");
        }
        if (!booking.getPaymentStatus().canTransitionTo(PaymentStatus.PAID)) {
            throw new PaymentException("This payment has already been settled or cancelled.");
        }
        // Conditional update: only one request can win, and only while the booking is still completed and unpaid.
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("id").is(booking.getId()),
                ownership,
                Criteria.where("bookingStatus").is(BookingStatus.COMPLETED),
                Criteria.where("paymentStatus").is(PaymentStatus.PENDING)));
        Instant now = Instant.now(clock);
        Booking updated = mongoTemplate.findAndModify(query,
                new Update().set("paymentStatus", PaymentStatus.PAID).set("paidAt", now).set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (updated == null) {
            throw new PaymentException("This payment was just updated. Please reload the page.");
        }
        eventPublisher.publishEvent(new CashPaymentConfirmedEvent(
                updated.getId(), updated.getCustomerId(), updated.getWorkerId()));
    }
}
