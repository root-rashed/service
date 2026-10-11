package com.example.marketplace.worker.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.security.AppUserDetails;
import com.example.marketplace.user.model.User;
import com.example.marketplace.worker.dto.WorkerDashboardSummary;
import com.example.marketplace.worker.dto.WorkerEarningsDto;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import com.example.marketplace.worker.model.WorkerProfile;
import com.example.marketplace.worker.service.WorkerDashboardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders the professional dashboard template.
 *
 * <p>The interesting failure mode for a Thymeleaf page is an expression that only breaks on real
 * data, so both dashboard states are exercised: a published professional with bookings, and one
 * who has not created a profile yet. Without this, a template typo only shows up at runtime.
 */
@WebMvcTest(WorkerDashboardController.class)
class WorkerDashboardControllerTest {

    private static final String WORKER_ID = "worker-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkerDashboardService workerDashboardService;

    /**
     * The application enables caching, but a sliced web test has no real CacheManager. The
     * dashboard template never reads through the cache, so an in-memory one is enough to let the
     * context start.
     */
    @TestConfiguration
    static class CacheStub {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Test
    @DisplayName("renders the dashboard for a published professional with work and earnings")
    void rendersPopulatedDashboard() throws Exception {
        given(workerDashboardService.getDashboard(WORKER_ID)).willReturn(populatedSummary());

        mockMvc.perform(get("/worker/dashboard").with(user(workerPrincipal())))
                .andExpect(status().isOk())
                .andExpect(view().name("worker/dashboard"))
                .andExpect(content().string(containsString("Deep cleaning")))
                .andExpect(content().string(containsString("Cash collected")))
                .andExpect(content().string(containsString("View my public profile")));
    }

    @Test
    @DisplayName("renders the dashboard for a professional who has not created a profile")
    void rendersDashboardWithoutProfile() throws Exception {
        given(workerDashboardService.getDashboard(WORKER_ID)).willReturn(
                new WorkerDashboardSummary(null, List.of(), List.of(), List.of(),
                        WorkerEarningsDto.empty(), 0, 0));

        mockMvc.perform(get("/worker/dashboard").with(user(workerPrincipal())))
                .andExpect(status().isOk())
                .andExpect(view().name("worker/dashboard"))
                .andExpect(content().string(containsString("Set up your professional profile")))
                .andExpect(content().string(containsString("No completed jobs yet")));
    }

    // ---------- Fixtures ----------

    private WorkerDashboardSummary populatedSummary() {
        return new WorkerDashboardSummary(
                approvedProfile(),
                List.of(BookingDto.forWorker(pendingBooking())),
                List.of(BookingDto.forWorker(acceptedBooking())),
                List.of(BookingDto.forWorker(completedBooking())),
                new WorkerEarningsDto(12L, new BigDecimal("8400.00"), new BigDecimal("420.00"),
                        new BigDecimal("7980.00"), new BigDecimal("6300.00"), new BigDecimal("2100.00")),
                4, 3);
    }

    private WorkerProfileDto approvedProfile() {
        WorkerProfile profile = WorkerProfile.builder()
                .id("profile-1")
                .userId(WORKER_ID)
                .slug("rashid-cleaning")
                .displayName("Rashid Karim")
                .headline("Home cleaner")
                .bio("Ten years of experience.")
                .experienceYears(10)
                .skills(List.of("Deep cleaning", "Kitchen"))
                .area("Mirpur")
                .verificationStatus(WorkerVerificationStatus.APPROVED)
                .ratingAverage(4.8)
                .ratingCount(21)
                .completedJobs(120)
                .build();
        return WorkerProfileDto.from(profile);
    }

    private Booking pendingBooking() {
        return baseBooking("Deep cleaning", BookingStatus.PENDING, PaymentStatus.PENDING);
    }

    private Booking acceptedBooking() {
        return baseBooking("Kitchen deep clean", BookingStatus.WORKER_ACCEPTED, PaymentStatus.PENDING);
    }

    private Booking completedBooking() {
        return baseBooking("Bathroom cleaning", BookingStatus.COMPLETED, PaymentStatus.PAID);
    }

    private Booking baseBooking(String title, BookingStatus status, PaymentStatus paymentStatus) {
        return Booking.builder()
                .id("booking-1")
                .serviceTitle(title)
                .serviceSlug("deep-cleaning")
                .workerId(WORKER_ID)
                .workerName("Rashid Karim")
                .customerId("customer-1")
                .customerName("Ayesha Siddiqua")
                .customerPhone("+8801700000000")
                .bookingDate("2026-10-20")
                .startMinute(600)
                .endMinute(720)
                .durationMinutes(120)
                .address("House 12, Road 5, Mirpur, Dhaka")
                .servicePrice(new BigDecimal("800.00"))
                .platformFee(new BigDecimal("40.00"))
                .totalAmount(new BigDecimal("840.00"))
                .paymentStatus(paymentStatus)
                .bookingStatus(status)
                .createdAt(Instant.parse("2026-10-06T00:00:00Z"))
                .build();
    }

    private AppUserDetails workerPrincipal() {
        User user = User.builder()
                .id(WORKER_ID)
                .name("Rashid Karim")
                .email("rashid@example.com")
                .passwordHash("hash")
                .role(Role.WORKER)
                .status(UserStatus.ACTIVE)
                .build();
        return new AppUserDetails(user);
    }

    /** Guards against the fixture drifting away from the id the controller is asked about. */
    @Test
    @DisplayName("reads the dashboard for the signed-in professional, not a request parameter")
    void scopesToAuthenticatedUser() throws Exception {
        given(workerDashboardService.getDashboard(anyString())).willReturn(populatedSummary());

        mockMvc.perform(get("/worker/dashboard").with(user(workerPrincipal())))
                .andExpect(status().isOk());
    }
}