package com.example.marketplace.schedule.model;

import java.time.DayOfWeek;
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
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A worker's weekly working hours plus blocked dates (vacation / unavailable days). One per worker.
 *
 * Storage conventions used across the booking modules (timezone-proof, easy to query):
 * times are minutes from midnight (09:00 = 540), dates are ISO strings ("2026-10-12").
 */
@Document(collection = "worker_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerSchedule {

    @Id
    private String id;

    /** The worker's user id. */
    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private List<DaySchedule> days = new ArrayList<>();

    @Builder.Default
    private List<BlockedDate> blockedDates = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Working hours for one weekday (one continuous range). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaySchedule {
        private DayOfWeek dayOfWeek;
        private boolean working;
        private int startMinute;
        private int endMinute;
    }

    /** A single unavailable calendar day. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockedDate {
        /** ISO date, e.g. 2026-10-12. */
        private String date;
        private String reason;
    }
}
