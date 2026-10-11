package com.example.marketplace.notification.service;

import com.example.marketplace.booking.event.BookingStatusChangedEvent;
import com.example.marketplace.booking.event.CashPaymentConfirmedEvent;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.NotificationType;
import com.example.marketplace.notification.email.EmailSender;
import com.example.marketplace.review.event.ReviewCreatedEvent;
import com.example.marketplace.schedule.dto.TimeSlot;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into in-app notifications (and a short email for the events where
 * email genuinely helps).
 *
 * This is the only place that decides who is told what. It reads recipients from the booking
 * and the user records, never from the request, and it never blocks the caller: storing an
 * in-app message is a single insert and the email sender runs on its own executor.
 * Payment and live booking state are never cached; this listener writes rows that are read fresh.
 */
@Slf4j
@Component
public class DomainEventNotificationListener {

    /** Which side of a booking gets a message for each new status. */
    private static final Map<BookingStatus, Set<Who>> RECIPIENTS = new EnumMap<>(BookingStatus.class);
    private static final Map<BookingStatus, NotificationType> TYPE_BY_STATUS = new EnumMap<>(BookingStatus.class);

    private enum Who { CUSTOMER, WORKER }

    static {
        RECIPIENTS.put(BookingStatus.PENDING, EnumSet.of(Who.WORKER));
        TYPE_BY_STATUS.put(BookingStatus.PENDING, NotificationType.BOOKING_REQUEST);

        RECIPIENTS.put(BookingStatus.WORKER_ACCEPTED, EnumSet.of(Who.CUSTOMER));
        TYPE_BY_STATUS.put(BookingStatus.WORKER_ACCEPTED, NotificationType.BOOKING_ACCEPTED);

        RECIPIENTS.put(BookingStatus.WORKER_REJECTED, EnumSet.of(Who.CUSTOMER));
        TYPE_BY_STATUS.put(BookingStatus.WORKER_REJECTED, NotificationType.BOOKING_REJECTED);

        RECIPIENTS.put(BookingStatus.IN_PROGRESS, EnumSet.of(Who.CUSTOMER));
        TYPE_BY_STATUS.put(BookingStatus.IN_PROGRESS, NotificationType.BOOKING_IN_PROGRESS);

        RECIPIENTS.put(BookingStatus.COMPLETED, EnumSet.of(Who.CUSTOMER));
        TYPE_BY_STATUS.put(BookingStatus.COMPLETED, NotificationType.BOOKING_COMPLETED);

        RECIPIENTS.put(BookingStatus.CANCELLED, EnumSet.of(Who.CUSTOMER, Who.WORKER));
        TYPE_BY_STATUS.put(BookingStatus.CANCELLED, NotificationType.BOOKING_CANCELLED);
    }

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailSender emailSender;
    private final String appName;

    public DomainEventNotificationListener(BookingRepository bookingRepository,
                                           UserRepository userRepository,
                                           NotificationService notificationService,
                                           EmailSender emailSender,
                                           @Value("${app.name:ServiZone}") String appName) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
        this.appName = appName;
    }

    // ---------- Booking lifecycle ----------

    @EventListener
    public void onBookingStatusChanged(BookingStatusChangedEvent event) {
        NotificationType type = TYPE_BY_STATUS.get(event.to());
        Set<Who> recipients = RECIPIENTS.get(event.to());
        if (type == null || recipients == null) {
            return;
        }
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        String when = scheduleLabel(booking);
        for (Who who : recipients) {
            boolean isCustomer = who == Who.CUSTOMER;
            String userId = isCustomer ? booking.getCustomerId() : booking.getWorkerId();
            // The link has to point at a page that recipient is actually allowed to open.
            notificationService.notify(userId, type, type.getDefaultTitle(),
                    messageFor(type, booking, when),
                    isCustomer ? "/booking/" + booking.getId() : "/worker/bookings");
            sendMail(userId, type, booking, when);
        }
    }

    @EventListener
    public void onCashPaymentConfirmed(CashPaymentConfirmedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        notificationService.notify(booking.getCustomerId(), NotificationType.CASH_PAID,
                "Payment received",
                "Cash of " + amount(booking.getTotalAmount()) + " for \"" + booking.getServiceTitle()
                        + "\" has been recorded by the professional.",
                "/booking/" + booking.getId());
    }

    @EventListener
    public void onReviewCreated(ReviewCreatedEvent event) {
        notificationService.notify(event.workerId(), NotificationType.REVIEW_RECEIVED,
                "New " + event.rating() + "-star review",
                "A customer left you a review. Open your reviews to reply to future customers with it.",
                "/worker/reviews");
    }

    // ---------- Message wording ----------

    private String messageFor(NotificationType type, Booking b, String when) {
        return switch (type) {
            case BOOKING_REQUEST -> b.getCustomerName() + " requested \"" + b.getServiceTitle()
                    + "\" on " + when + ".";
            case BOOKING_ACCEPTED -> b.getWorkerName() + " confirmed \"" + b.getServiceTitle()
                    + "\" for " + when + ".";
            case BOOKING_REJECTED -> b.getWorkerName() + " can't take \"" + b.getServiceTitle()
                    + "\" on " + when + ".";
            case BOOKING_CANCELLED -> "Your booking \"" + b.getServiceTitle() + "\" on " + when
                    + " was cancelled.";
            case BOOKING_IN_PROGRESS -> b.getWorkerName() + " has started \"" + b.getServiceTitle()
                    + "\". Total due: " + amount(b.getTotalAmount()) + " in cash.";
            case BOOKING_COMPLETED -> "\"" + b.getServiceTitle() + "\" is marked complete. "
                    + "Pay " + amount(b.getTotalAmount()) + " in cash.";
            default -> b.getServiceTitle() + " - " + when;
        };
    }

    private static final Set<NotificationType> EMAILED = EnumSet.of(
            NotificationType.BOOKING_REQUEST,
            NotificationType.BOOKING_ACCEPTED);

    private void sendMail(String userId, NotificationType type, Booking b, String when) {
        // Only the events where the recipient genuinely needs to know outside the app.
        if (!EMAILED.contains(type)) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String subject = appName + ": " + type.getDefaultTitle().toLowerCase(Locale.ROOT);
        String body = messageFor(type, b, when) + "\n\n"
                + "Service: " + b.getServiceTitle() + "\n"
                + "When: " + when + "\n"
                + "Total: " + amount(b.getTotalAmount()) + " (pay in cash after the job)\n\n"
                + "Log in to " + appName + " to see the full details and reply to the customer.\n\n"
                + "- The " + appName + " team";
        emailSender.send(user.getEmail(), subject, body);
    }

    private String scheduleLabel(Booking b) {
        String time = TimeSlot.format12(b.getStartMinute()) + " - " + TimeSlot.format12(b.getEndMinute());
        return b.getBookingDate() + " at " + time;
    }

    private String amount(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
