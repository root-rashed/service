package com.example.marketplace.worker.dto;

import com.example.marketplace.booking.dto.BookingDto;
import java.util.List;
import lombok.Value;

/**
 * Everything the professional dashboard needs in one read: their verification standing, the work
 * waiting on them right now, what is coming up, and what they have earned.
 *
 * @param profile                  null when the professional has not created a profile yet
 * @param awaitingResponse         requests the professional still has to accept or decline
 * @param upcomingJobs             confirmed jobs on the next few days, soonest first
 * @param recentJobs               the last few finished jobs, for a quick "how did I do" glance
 * @param earnings                 totals from their own completed bookings
 * @param totalServices            listings they have published in total
 * @param activeServices           listings currently visible on the public catalog
 */
@Value
public class WorkerDashboardSummary {

    WorkerProfileDto profile;
    List<BookingDto> awaitingResponse;
    List<BookingDto> upcomingJobs;
    List<BookingDto> recentJobs;
    WorkerEarningsDto earnings;
    long totalServices;
    long activeServices;

    /** True when the professional has a profile that the public catalog can show. */
    public boolean isPublished() {
        return profile != null && profile.getVerificationStatus().isPubliclyVisible();
    }

    /** True when the professional has not created a profile yet. */
    public boolean isMissingProfile() {
        return profile == null;
    }
}