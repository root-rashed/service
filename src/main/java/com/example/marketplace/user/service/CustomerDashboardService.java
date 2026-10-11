package com.example.marketplace.user.service;

import com.example.marketplace.booking.service.BookingService;
import com.example.marketplace.favorite.service.FavoriteService;
import com.example.marketplace.notification.service.NotificationService;
import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.user.dto.CustomerDashboardSummary;
import org.springframework.stereotype.Service;

/**
 * Assembles the customer dashboard from the other modules.
 *
 * <p>Exists so {@code CustomerController} only forwards a view name: which modules the dashboard
 * shows, and how many rows each one gets, is a single decision made here instead of in a
 * controller. Each list is a short, freshly-read slice — a dashboard must never show stale counts.
 */
@Service
public class CustomerDashboardService {

    private static final int SAVED_SERVICE_LIMIT = 4;
    private static final int REVIEW_LIMIT = 3;
    private static final int NOTIFICATION_LIMIT = 4;

    private final AccountProfileService accountProfileService;
    private final BookingService bookingService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final NotificationService notificationService;

    public CustomerDashboardService(AccountProfileService accountProfileService,
                                    BookingService bookingService,
                                    FavoriteService favoriteService,
                                    ReviewService reviewService,
                                    NotificationService notificationService) {
        this.accountProfileService = accountProfileService;
        this.bookingService = bookingService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.notificationService = notificationService;
    }

    /** @param userId the signed-in customer's user id, taken from the authenticated principal */
    public CustomerDashboardSummary getDashboard(String userId) {
        var saved = favoriteService.list(userId, 0);
        return new CustomerDashboardSummary(
                accountProfileService.getProfile(userId),
                bookingService.getDashboardSummary(userId),
                saved.getContent().stream().limit(SAVED_SERVICE_LIMIT).toList(),
                saved.getTotalElements(),
                reviewService.getLatestByCustomer(userId, REVIEW_LIMIT),
                notificationService.recent(userId, NOTIFICATION_LIMIT));
    }
}