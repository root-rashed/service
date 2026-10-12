package com.example.marketplace.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketplace.booking.event.BookingStatusChangedEvent;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.NotificationType;
import com.example.marketplace.notification.email.CapturingEmailSender;
import com.example.marketplace.notification.email.EmailMessage;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins that the booking notification workflow still decides the recipient, subject and body
 * server-side, and that only the events that warrant an email produce one - now delivered
 * through the configured {@code EmailSender}.
 */
class DomainEventNotificationListenerTest {

    private BookingRepository bookingRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private CapturingEmailSender emailSender;
    private DomainEventNotificationListener listener;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        emailSender = new CapturingEmailSender();
        listener = new DomainEventNotificationListener(
                bookingRepository, userRepository, notificationService, emailSender, "ServiZone");
    }

    private Booking booking() {
        return Booking.builder()
                .id("b1")
                .customerId("c1")
                .workerId("w1")
                .serviceTitle("Math tutoring")
                .workerName("Rashid Khan")
                .customerName("Ayesha Rahman")
                .bookingDate("2026-10-15")
                .startMinute(9 * 60)
                .endMinute(10 * 60)
                .totalAmount(new BigDecimal("105"))
                .build();
    }

    @Test
    void acceptedBookingEmailsTheCustomerWithTheBookingDetails() {
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking()));
        when(userRepository.findById("c1")).thenReturn(Optional.of(
                User.builder().id("c1").email("ayesha@example.com").name("Ayesha Rahman").build()));

        listener.onBookingStatusChanged(new BookingStatusChangedEvent(
                "b1", "c1", "w1", BookingStatus.PENDING, BookingStatus.WORKER_ACCEPTED));

        assertThat(emailSender.sent()).hasSize(1);
        EmailMessage email = emailSender.sent().get(0);
        assertThat(email.to()).isEqualTo("ayesha@example.com");
        assertThat(email.subject()).isEqualTo("ServiZone: booking confirmed");
        assertThat(email.text()).contains("Math tutoring");
        assertThat(email.text()).contains("105");

        verify(notificationService).notify(eq("c1"), eq(NotificationType.BOOKING_ACCEPTED),
                anyString(), anyString(), eq("/booking/b1"));
    }

    @Test
    void cancellationNotifiesInAppButSendsNoEmail() {
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking()));

        listener.onBookingStatusChanged(new BookingStatusChangedEvent(
                "b1", "c1", "w1", BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED));

        assertThat(emailSender.sent()).isEmpty();
        verify(notificationService).notify(eq("c1"), eq(NotificationType.BOOKING_CANCELLED),
                anyString(), anyString(), anyString());
    }
}