package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminStatusChangeDto;
import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Booking oversight for administrators.
 *
 * Read-only by design except for one escape hatch: {@link #cancel} delegates to
 * BookingService so the state machine, the audit trail and the time-slot lock release all
 * stay in one place. Administrators see the customer's phone number, which is why the
 * admin-specific BookingDto view is used.
 */
@Service
public class AdminBookingService {

    private static final int PAGE_SIZE = 15;
    private static final int MAX_KEYWORD_LENGTH = 60;

    /** Statuses that still hold a professional's calendar, and cash that is still owed. */
    private static final List<BookingStatus> OPEN_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.WORKER_ACCEPTED, BookingStatus.IN_PROGRESS);

    private final MongoTemplate mongoTemplate;

    public AdminBookingService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param status  optional booking-status filter; blank or unknown means all statuses
     * @param payment optional payment-status filter
     * @param query   optional free text matched against the service, customer and worker names
     */
    public Page<BookingDto> search(String status, String payment, String query, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id")));
        Query mongoQuery = Query.query(buildCriteria(status, payment, query));
        long total = mongoTemplate.count(mongoQuery, Booking.class);
        mongoQuery.skip((long) pageable.getPageNumber() * PAGE_SIZE).limit(PAGE_SIZE);

        List<BookingDto> content = mongoTemplate.find(mongoQuery, Booking.class).stream()
                .map(BookingDto::forAdmin)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    /** Completed jobs whose cash has not been confirmed yet. */
    public Page<BookingDto> getUnpaid(int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.asc("bookingDate"), Sort.Order.asc("startMinute")));
        Query mongoQuery = Query.query(new Criteria()
                .and("bookingStatus").is(BookingStatus.COMPLETED)
                .and("paymentStatus").is(PaymentStatus.PENDING));
        long total = mongoTemplate.count(mongoQuery, Booking.class);
        mongoQuery.skip((long) pageable.getPageNumber() * PAGE_SIZE).limit(PAGE_SIZE);

        List<BookingDto> content = mongoTemplate.find(mongoQuery, Booking.class).stream()
                .map(BookingDto::forAdmin)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    public BookingDto get(String bookingId) {
        return BookingDto.forAdmin(find(bookingId));
    }

    /** The stored booking, for callers that need the raw status history. */
    public Booking getBooking(String bookingId) {
        return find(bookingId);
    }

    /** The audit trail, formatted for the detail page. */
    public List<AdminStatusChangeDto> getHistory(String bookingId) {
        return AdminStatusChangeDto.from(find(bookingId).getStatusHistory());
    }

    /**
     * Whether an administrator can confirm the cash for this booking: a completed job that has
     * not already been settled. This mirrors the customer-facing rule, so the two paths cannot
     * disagree about what is payable.
     */
    public boolean canMarkPaid(BookingDto booking) {
        return booking != null
                && booking.getBookingStatus() == BookingStatus.COMPLETED
                && booking.getPaymentStatus() == PaymentStatus.PENDING;
    }

    private Booking find(String bookingId) {
        Booking booking = mongoTemplate.findById(bookingId, Booking.class);
        if (booking == null) {
            throw ResourceNotFoundException.of("Booking");
        }
        return booking;
    }

    /** Counts for every booking status, so the moderation tabs can show totals. */
    public Map<BookingStatus, Long> countsByStatus() {
        Map<BookingStatus, Long> counts = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status, mongoTemplate.count(
                    Query.query(Criteria.where("bookingStatus").is(status)), Booking.class));
        }
        return counts;
    }

    public List<BookingStatus> statuses() {
        return Arrays.asList(BookingStatus.values());
    }

    public List<PaymentStatus> paymentStatuses() {
        return Arrays.asList(PaymentStatus.values());
    }

    /** True when the booking still occupies a professional's calendar. */
    public boolean isOpen(BookingStatus status) {
        return OPEN_STATUSES.contains(status);
    }

    // ---------- Internals ----------

    private Criteria buildCriteria(String status, String payment, String query) {
        List<Criteria> filters = new ArrayList<>(3);
        BookingStatus parsedStatus = parseEnum(BookingStatus.class, status);
        if (parsedStatus != null) {
            filters.add(Criteria.where("bookingStatus").is(parsedStatus));
        }
        PaymentStatus parsedPayment = parseEnum(PaymentStatus.class, payment);
        if (parsedPayment != null) {
            filters.add(Criteria.where("paymentStatus").is(parsedPayment));
        }
        String term = trim(query);
        if (!term.isEmpty()) {
            // Pattern.quote keeps user input out of the regex engine entirely.
            String regex = Pattern.quote(term);
            filters.add(new Criteria().orOperator(
                    Criteria.where("serviceTitle").regex(regex, "i"),
                    Criteria.where("customerName").regex(regex, "i"),
                    Criteria.where("workerName").regex(regex, "i"),
                    Criteria.where("address").regex(regex, "i")));
        }
        if (filters.isEmpty()) {
            return new Criteria();
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new Criteria().andOperator(filters.toArray(new Criteria[0]));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
    }
}