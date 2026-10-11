package com.example.marketplace.schedule.service;

import com.example.marketplace.schedule.dto.TimeRange;
import com.example.marketplace.schedule.dto.TimeSlot;
import com.example.marketplace.schedule.model.WorkerSchedule;
import com.example.marketplace.schedule.model.WorkerSchedule.DaySchedule;
import com.example.marketplace.schedule.repository.WorkerScheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Computes real availability from: weekly working hours, blocked dates, service duration,
 * minimum notice / maximum advance, and the time already taken by other bookings.
 *
 * Always reads live data and is never cached. The booking module must call isSlotAvailable(...)
 * again, server-side, at the moment it creates a booking: the browser's list is only a hint.
 */
@Service
public class AvailabilityService {

    private final WorkerScheduleRepository scheduleRepository;
    private final ObjectProvider<BusyTimeSource> busyTimeSources;
    private final Clock clock;
    private final int minNoticeMinutes;
    private final int maxAdvanceDays;
    private final int slotStepMinutes;

    public AvailabilityService(WorkerScheduleRepository scheduleRepository,
                               ObjectProvider<BusyTimeSource> busyTimeSources,
                               Clock clock,
                               @Value("${app.booking.min-notice-minutes:120}") int minNoticeMinutes,
                               @Value("${app.booking.max-advance-days:60}") int maxAdvanceDays,
                               @Value("${app.booking.slot-step-minutes:30}") int slotStepMinutes) {
        this.scheduleRepository = scheduleRepository;
        this.busyTimeSources = busyTimeSources;
        this.clock = clock;
        this.minNoticeMinutes = minNoticeMinutes;
        this.maxAdvanceDays = maxAdvanceDays;
        this.slotStepMinutes = slotStepMinutes;
    }

    public List<TimeSlot> getAvailableSlots(String workerUserId, int durationMinutes, LocalDate date) {
        return computeRanges(workerUserId, durationMinutes, date).stream().map(TimeSlot::of).toList();
    }

    /** Authoritative check used when a booking is created. */
    public boolean isSlotAvailable(String workerUserId, int durationMinutes, LocalDate date, int startMinute) {
        return computeRanges(workerUserId, durationMinutes, date).stream()
                .anyMatch(r -> r.startMinute() == startMinute);
    }

    // ---------- Internals ----------

    private List<TimeRange> computeRanges(String workerUserId, int durationMinutes, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(maxAdvanceDays))) {
            return List.of();
        }

        Optional<WorkerSchedule> schedule = scheduleRepository.findByUserId(workerUserId);
        if (schedule.isEmpty()) {
            return List.of(); // no published hours means not bookable
        }
        String iso = date.toString();
        if (schedule.get().getBlockedDates().stream().anyMatch(b -> b.getDate().equals(iso))) {
            return List.of();
        }
        DaySchedule day = schedule.get().getDays().stream()
                .filter(d -> d.getDayOfWeek() == date.getDayOfWeek())
                .findFirst()
                .orElse(null);
        if (day == null || !day.isWorking()) {
            return List.of();
        }

        int earliestStart = 0;
        if (date.equals(today)) {
            LocalTime now = LocalTime.now(clock);
            earliestStart = now.getHour() * 60 + now.getMinute() + minNoticeMinutes;
        }

        List<TimeRange> busy = new ArrayList<>();
        busyTimeSources.orderedStream().forEach(source -> busy.addAll(source.busyRanges(workerUserId, date)));

        return SlotCalculator.compute(day.getStartMinute(), day.getEndMinute(),
                durationMinutes, slotStepMinutes, earliestStart, busy);
    }
}
