package com.example.marketplace.user.dto;

import com.example.marketplace.booking.dto.CustomerBookingSummary;
import com.example.marketplace.notification.dto.NotificationDto;
import com.example.marketplace.review.dto.ReviewDto;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import java.util.List;
import lombok.Value;

/**
 * Everything the customer dashboard shows, gathered by one service call so the controller stays
 * free of business decisions.
 *
 * @param savedServices      the newest saved services (still publicly visible ones only)
 * @param savedServiceTotal  how many services are saved in total, including ones since hidden
 * @param recentReviews      reviews this customer wrote, newest first
 * @param notifications      the newest messages, read or not
 */
@Value
public class CustomerDashboardSummary {

    AccountProfileDto account;
    CustomerBookingSummary bookings;
    List<ServiceListingDto> savedServices;
    long savedServiceTotal;
    List<ReviewDto> recentReviews;
    List<NotificationDto> notifications;

    /** True when nothing at all has happened yet, so the page can show a first-run prompt. */
    public boolean isEmpty() {
        return (bookings == null || (bookings.getUpcoming() == null && bookings.getRecent().isEmpty()))
                && savedServiceTotal == 0
                && recentReviews.isEmpty();
    }
}