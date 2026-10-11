package com.example.marketplace.booking.model;

import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentMethod;
import com.example.marketplace.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer's booking of a worker's service.
 *
 * Money is a SNAPSHOT taken on the server when the booking is created; later changes to the service
 * price never alter existing bookings. Payment state (CASH only) is kept separate from booking state.
 * Dates are ISO strings and times are minutes from midnight (same convention as the schedule).
 * Customer and worker ids are user ids and are always set server-side.
 */
@Document(collection = "bookings")
@CompoundIndex(name = "worker_date_status", def = "{'workerId': 1, 'bookingDate': 1, 'bookingStatus': 1}")
@CompoundIndex(name = "worker_status_date", def = "{'workerId': 1, 'bookingStatus': 1, 'bookingDate': 1}")
@CompoundIndex(name = "customer_created", def = "{'customerId': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    private String id;

    private String customerId;
    private String workerId;
    private String serviceId;

    // Snapshots, so history stays readable even if the service or accounts change later.
    private String serviceTitle;
    private String serviceSlug;
    private String workerName;
    private String customerName;
    private String customerPhone;

    /** ISO date, e.g. 2026-10-12. */
    private String bookingDate;
    private int startMinute;
    private int endMinute;
    private int durationMinutes;

    private String address;
    private String customerNote;

    private BigDecimal servicePrice;
    private BigDecimal platformFee;
    private BigDecimal totalAmount;

    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private Instant paidAt;

    @Builder.Default
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    @Builder.Default
    private List<StatusChange> statusHistory = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** One entry of the audit trail. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusChange {
        private BookingStatus status;
        private Instant at;
        /** CUSTOMER, WORKER or SYSTEM. */
        private String by;
    }
}
