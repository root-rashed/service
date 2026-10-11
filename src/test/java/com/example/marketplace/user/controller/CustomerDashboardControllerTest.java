package com.example.marketplace.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.dto.CustomerBookingSummary;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.NotificationType;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.notification.dto.NotificationDto;
import com.example.marketplace.notification.model.Notification;
import com.example.marketplace.review.dto.ReviewDto;
import com.example.marketplace.review.model.Review;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.security.SessionPrincipalRefresher;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.user.dto.AccountProfileDto;
import com.example.marketplace.user.dto.CustomerDashboardSummary;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.service.AccountProfileService;
import com.example.marketplace.user.service.CustomerDashboardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders the customer dashboard template.
 *
 * <p>Covers both a customer who has used the marketplace and one who has just signed up, because
 * every card has its own empty state and those branches only ever fail once real bookings, saved
 * services and messages exist.
 */
@WebMvcTest(CustomerController.class)
class CustomerDashboardControllerTest {

    private static final String CUSTOMER_ID = "customer-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerDashboardService customerDashboardService;

    /** The other two constructor dependencies of the controller; unused by the dashboard route. */
    @MockitoBean
    private AccountProfileService accountProfileService;

    @MockitoBean
    private SessionPrincipalRefresher principalRefresher;

    /** The application enables caching; a sliced web test has no real CacheManager. */
    @TestConfiguration
    static class CacheStub {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Test
    @DisplayName("renders the dashboard for a customer with bookings, saves, reviews and messages")
    void rendersPopulatedDashboard() throws Exception {
        given(customerDashboardService.getDashboard(CUSTOMER_ID)).willReturn(
                new CustomerDashboardSummary(
                new AccountProfileDto("Ayesha Siddiqua", "ayesha@example.com", "+8801700000000", null),
                new CustomerBookingSummary(BookingDto.forCustomer(upcomingBooking(), true), List.of()),
                List.of(savedService()),
                7,
                List.of(review()),
                List.of(notification())));

        mockMvc.perform(get("/customer/dashboard").with(user(customerPrincipal())))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/dashboard"))
                .andExpect(content().string(containsString("Deep cleaning")))
                .andExpect(content().string(containsString("Saved services")))
                .andExpect(content().string(containsString("Your reviews")))
                .andExpect(content().string(containsString("Messages")));
    }

    @Test
    @DisplayName("renders every empty state for a brand new customer")
    void rendersEmptyDashboard() throws Exception {
        given(customerDashboardService.getDashboard(CUSTOMER_ID)).willReturn(
                new CustomerDashboardSummary(
                new AccountProfileDto("Ayesha Siddiqua", "ayesha@example.com", null, null),
                new CustomerBookingSummary(null, List.of()),
                List.of(), 0, List.of(), List.of()));

        mockMvc.perform(get("/customer/dashboard").with(user(customerPrincipal())))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/dashboard"))
                .andExpect(content().string(containsString("No upcoming bookings")))
                .andExpect(content().string(containsString("Nothing saved yet")))
                .andExpect(content().string(containsString("No reviews yet")))
                .andExpect(content().string(containsString("No messages")));
    }

    // ---------- Fixtures ----------

    private Booking upcomingBooking() {
        return Booking.builder()
                .id("booking-1")
                .serviceTitle("Deep cleaning")
                .serviceSlug("deep-cleaning")
                .workerId("worker-1")
                .workerName("Rashid Karim")
                .customerId(CUSTOMER_ID)
                .customerName("Ayesha Siddiqua")
                .bookingDate("2026-10-20")
                .startMinute(600)
                .endMinute(720)
                .durationMinutes(120)
                .address("House 12, Mirpur, Dhaka")
                .servicePrice(new BigDecimal("800.00"))
                .platformFee(new BigDecimal("40.00"))
                .totalAmount(new BigDecimal("840.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .bookingStatus(BookingStatus.WORKER_ACCEPTED)
                .createdAt(Instant.parse("2026-10-06T00:00:00Z"))
                .build();
    }

    private ServiceListingDto savedService() {
        return new ServiceListingDto(
                "service-1", "deep-cleaning", "worker-1", "category-1", "Home Cleaning", "Deep cleaning",
                "A thorough clean.", List.of("Kitchen"), new BigDecimal("800.00"), null, "per visit",
                120, "Mirpur", 5, null, null, ServiceStatus.ACTIVE, 4.9, 21, "Top rated", "blue",
                "bi-house-door", "orange", List.of(), null);
    }

    private ReviewDto review() {
        Review review = Review.builder()
                .id("review-1")
                .bookingId("booking-1")
                .serviceId("service-1")
                .workerId("worker-1")
                .customerId(CUSTOMER_ID)
                .serviceTitle("Deep cleaning")
                .serviceSlug("deep-cleaning")
                .authorName("Ayesha Siddiqua")
                .rating(5)
                .comment("Excellent work, very punctual.")
                .createdAt(Instant.parse("2026-10-05T00:00:00Z"))
                .build();
        return ReviewDto.from(review);
    }

    private NotificationDto notification() {
        Notification notification = Notification.builder()
                .id("notification-1")
                .userId(CUSTOMER_ID)
                .title("Booking accepted")
                .message("Rashid Karim accepted your request.")
                .link("/booking/booking-1")
                .bookingId("booking-1")
                .type(NotificationType.BOOKING_ACCEPTED)
                .createdAt(Instant.parse("2026-10-06T00:00:00Z"))
                .build();
        return NotificationDto.from(notification);
    }

    private AppUserDetails customerPrincipal() {
        User user = User.builder()
                .id(CUSTOMER_ID)
                .name("Ayesha Siddiqua")
                .email("ayesha@example.com")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        return new AppUserDetails(user);
    }
}