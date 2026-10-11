package com.example.marketplace.worker.dto;

import java.math.BigDecimal;
import lombok.Value;

/**
 * What a professional has earned, derived from their own completed bookings only.
 *
 * <p>Everything here is cash: the customer pays the professional in person and the platform fee is
 * already included in the collected amount, so {@code netEarnings} is the gross value of the work
 * minus the platform's cut.
 */
@Value
public class WorkerEarningsDto {

    /** Number of jobs the professional has marked complete. */
    long completedJobs;

    /** Value of all completed jobs, before the platform fee. */
    BigDecimal grossEarnings;

    /** Platform fee charged on those completed jobs. */
    BigDecimal platformFees;

    /** {@code grossEarnings} minus {@code platformFees}: what the professional keeps. */
    BigDecimal netEarnings;

    /** Completed jobs whose cash has already been confirmed. */
    BigDecimal cashCollected;

    /** Completed jobs still awaiting the cash handover being recorded. */
    BigDecimal cashOutstanding;

    /** An all-zero result, used when a professional has not completed a job yet. */
    public static WorkerEarningsDto empty() {
        return new WorkerEarningsDto(0L, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}