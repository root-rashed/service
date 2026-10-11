package com.example.marketplace.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Value;

/** Simple operational report: monthly booking/cash totals plus leaderboards. */
@Value
public class AdminReport {

    /** One row per calendar month, oldest first. */
    List<MonthRow> months;
    List<TopServiceRow> topServices;
    List<TopWorkerRow> topWorkers;

    @Value
    public static class MonthRow {
        /** ISO "yyyy-MM". */
        String month;
        String monthLabel;
        long bookings;
        long completed;
        /** Value of all completed jobs in the month (before knowing whether cash was confirmed). */
        BigDecimal jobValue;
        /** Cash actually confirmed in the month. */
        BigDecimal cashCollected;
        /** Platform fee on the confirmed cash. */
        BigDecimal platformFees;
    }

    @Value
    public static class TopServiceRow {
        String title;
        long bookings;
        BigDecimal value;
    }

    @Value
    public static class TopWorkerRow {
        String displayName;
        String slug;
        long completed;
        BigDecimal value;
    }

    public boolean isEmpty() {
        return months.isEmpty() && topServices.isEmpty() && topWorkers.isEmpty();
    }
}