package com.example.marketplace.schedule.service;

import com.example.marketplace.schedule.dto.TimeRange;
import java.time.LocalDate;
import java.util.List;

/**
 * Anything that occupies a worker's time on a given date. The booking module provides an
 * implementation that returns the ranges of active bookings; until it exists, nothing is busy.
 * Implementations must read live data and must never cache.
 */
public interface BusyTimeSource {

    List<TimeRange> busyRanges(String workerUserId, LocalDate date);
}
