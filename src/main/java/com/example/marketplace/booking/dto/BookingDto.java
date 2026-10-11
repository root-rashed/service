package com.example.marketplace.booking.dto;

import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentMethod;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.schedule.dto.TimeSlot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Value;

/**
 * Read-only booking view. Built for a specific viewer: a worker only sees the customer's phone
 * number once the booking has been accepted.
 */
@Value
public class BookingDto {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter CREATED_LABEL =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    String serviceTitle;
    String serviceSlug;
    String workerName;
    String customerName;
    /** Null for workers until the booking is accepted. */
    String customerPhone;
    String bookingDate;
    String dateLabel;
    String timeLabel;
    int startMinute;
    int durationMinutes;
    String address;
    String customerNote;
    BigDecimal servicePrice;
    BigDecimal platformFee;
    BigDecimal totalAmount;
    PaymentMethod paymentMethod;
    PaymentStatus paymentStatus;
    BookingStatus bookingStatus;
    String statusLabel;
    String statusTone;
    String paymentLabel;
    /** True if the customer may still cancel (computed by the service from the cancellation rule). */
    boolean canCancel;
    String createdOn;

    public static BookingDto forCustomer(Booking b, boolean canCancel) {
        return build(b, b.getCustomerPhone(), canCancel);
    }

    public static BookingDto forWorker(Booking b) {
        boolean contactVisible = b.getBookingStatus() == BookingStatus.WORKER_ACCEPTED
                || b.getBookingStatus() == BookingStatus.IN_PROGRESS
                || b.getBookingStatus() == BookingStatus.COMPLETED;
        return build(b, contactVisible ? b.getCustomerPhone() : null, false);
    }

    /** Moderation view: an administrator needs the full contact details to resolve a dispute. */
    public static BookingDto forAdmin(Booking b) {
        return build(b, b.getCustomerPhone(), false);
    }

    private static BookingDto build(Booking b, String phone, boolean canCancel) {
        return new BookingDto(
                b.getId(),
                b.getServiceTitle(),
                b.getServiceSlug(),
                b.getWorkerName(),
                b.getCustomerName(),
                phone,
                b.getBookingDate(),
                LocalDate.parse(b.getBookingDate()).format(DATE_LABEL),
                TimeSlot.format12(b.getStartMinute()) + " - " + TimeSlot.format12(b.getEndMinute()),
                b.getStartMinute(),
                b.getDurationMinutes(),
                b.getAddress(),
                b.getCustomerNote(),
                b.getServicePrice(),
                b.getPlatformFee(),
                b.getTotalAmount(),
                b.getPaymentMethod(),
                b.getPaymentStatus(),
                b.getBookingStatus(),
                statusLabel(b.getBookingStatus()),
                statusTone(b.getBookingStatus()),
                paymentLabel(b.getPaymentStatus()),
                canCancel,
                b.getCreatedAt() == null ? "" : CREATED_LABEL.format(b.getCreatedAt()));
    }

    /** Public so the admin moderation views label a status exactly the same way. */
    public static String statusLabel(BookingStatus s) {
        return switch (s) {
            case PENDING -> "Awaiting confirmation";
            case WORKER_ACCEPTED -> "Confirmed";
            case WORKER_REJECTED -> "Declined";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    /** Maps to the badge colour classes: amber, green, red, blue. */
    public static String statusTone(BookingStatus s) {
        return switch (s) {
            case PENDING -> "amber";
            case WORKER_ACCEPTED, COMPLETED -> "green";
            case WORKER_REJECTED, CANCELLED -> "red";
            case IN_PROGRESS -> "blue";
        };
    }

    private static String paymentLabel(PaymentStatus s) {
        return switch (s) {
            case PENDING -> "Cash due";
            case PAID -> "Paid in cash";
            case CANCELLED -> "Cancelled";
        };
    }
}
