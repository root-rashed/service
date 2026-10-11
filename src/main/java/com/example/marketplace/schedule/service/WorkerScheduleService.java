package com.example.marketplace.schedule.service;

import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.schedule.dto.BlockedDateDto;
import com.example.marketplace.schedule.dto.BlockedDateForm;
import com.example.marketplace.schedule.dto.ScheduleForm;
import com.example.marketplace.schedule.dto.ScheduleForm.DayForm;
import com.example.marketplace.schedule.dto.TimeSlot;
import com.example.marketplace.schedule.dto.WeeklyHour;
import com.example.marketplace.schedule.model.WorkerSchedule;
import com.example.marketplace.schedule.model.WorkerSchedule.BlockedDate;
import com.example.marketplace.schedule.model.WorkerSchedule.DaySchedule;
import com.example.marketplace.schedule.repository.WorkerScheduleRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * A worker's weekly hours and blocked dates. The worker id always comes from the authenticated
 * principal, so a worker can only read or change their own schedule.
 */
@Service
public class WorkerScheduleService {

    private static final int MIN_RANGE_MINUTES = 30;
    private static final int MAX_BLOCK_SPAN_DAYS = 60;
    private static final int MAX_BLOCKED_DATES = 200;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    private final WorkerScheduleRepository repository;
    private final Clock clock;

    public WorkerScheduleService(WorkerScheduleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ---------- Weekly hours ----------

    /** The saved schedule as a form, or a sensible default (Mon-Fri 09:00-17:00) if none is saved yet. */
    public ScheduleForm getForm(String userId) {
        List<DaySchedule> days = repository.findByUserId(userId)
                .map(WorkerSchedule::getDays)
                .filter(d -> d != null && d.size() == 7)
                .orElseGet(WorkerScheduleService::defaultDays);

        Map<DayOfWeek, DaySchedule> byDay = new HashMap<>();
        days.forEach(d -> byDay.put(d.getDayOfWeek(), d));

        ScheduleForm form = new ScheduleForm();
        for (DayOfWeek day : DayOfWeek.values()) {
            DaySchedule ds = byDay.getOrDefault(day, new DaySchedule(day, false, 540, 1020));
            DayForm row = new DayForm();
            row.setDayOfWeek(day.name());
            row.setWorking(ds.isWorking());
            row.setStartTime(TimeSlot.format24(ds.getStartMinute()));
            row.setEndTime(TimeSlot.format24(ds.getEndMinute()));
            form.getDays().add(row);
        }
        return form;
    }

    public void saveWeeklyHours(String userId, ScheduleForm form) {
        List<DaySchedule> days = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();

        for (int i = 0; i < form.getDays().size(); i++) {
            DayForm row = form.getDays().get(i);
            DayOfWeek day = parseDay(row.getDayOfWeek());
            if (!row.isWorking()) {
                days.add(new DaySchedule(day, false, 540, 1020));
                continue;
            }
            Integer start = toMinutes(row.getStartTime());
            Integer end = toMinutes(row.getEndTime());
            if (start == null) {
                errors.put("days[" + i + "].startTime", "Enter a start time.");
            }
            if (end == null) {
                errors.put("days[" + i + "].endTime", "Enter an end time.");
            }
            if (start != null && end != null && end - start < MIN_RANGE_MINUTES) {
                errors.put("days[" + i + "].endTime", "End time must be at least 30 minutes after the start.");
            }
            if (errors.isEmpty()) {
                days.add(new DaySchedule(day, true, start, end));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Please fix the highlighted times.", errors);
        }

        WorkerSchedule schedule = repository.findByUserId(userId)
                .orElseGet(() -> WorkerSchedule.builder().userId(userId).build());
        schedule.setDays(days);
        repository.save(schedule);
    }

    /** Public "working hours" table. Empty if the worker has not saved a schedule yet. */
    public List<WeeklyHour> getWeeklySummary(String userId) {
        return repository.findByUserId(userId)
                .map(WorkerSchedule::getDays)
                .filter(d -> d != null && !d.isEmpty())
                .map(days -> days.stream()
                        .sorted(Comparator.comparing(DaySchedule::getDayOfWeek))
                        .map(d -> new WeeklyHour(
                                d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                                d.isWorking(),
                                d.isWorking()
                                        ? TimeSlot.format12(d.getStartMinute()) + " - " + TimeSlot.format12(d.getEndMinute())
                                        : "Closed"))
                        .toList())
                .orElse(List.of());
    }

    // ---------- Blocked dates ----------

    /** Upcoming blocked days (today onwards), earliest first. */
    public List<BlockedDateDto> getUpcomingBlockedDates(String userId) {
        String today = LocalDate.now(clock).toString();
        return repository.findByUserId(userId)
                .map(WorkerSchedule::getBlockedDates)
                .orElse(List.of()).stream()
                .filter(b -> b.getDate().compareTo(today) >= 0)
                .sorted(Comparator.comparing(BlockedDate::getDate))
                .map(b -> new BlockedDateDto(b.getDate(), LocalDate.parse(b.getDate()).format(LABEL), b.getReason()))
                .toList();
    }

    public void blockDates(String userId, BlockedDateForm form) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = form.getFromDate();
        LocalDate to = form.getToDate() == null ? from : form.getToDate();

        if (from.isBefore(today)) {
            throw new ValidationException("You can only block today or future dates.");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("The end date can't be before the start date.");
        }
        if (from.plusDays(MAX_BLOCK_SPAN_DAYS).isBefore(to)) {
            throw new ValidationException("You can block at most " + MAX_BLOCK_SPAN_DAYS + " days at a time.");
        }

        WorkerSchedule schedule = repository.findByUserId(userId)
                .orElseGet(() -> WorkerSchedule.builder().userId(userId).days(defaultDays()).build());
        List<BlockedDate> blocked = new ArrayList<>(schedule.getBlockedDates());
        // Keep the list from growing forever: drop days that are already in the past.
        blocked.removeIf(b -> b.getDate().compareTo(today.toString()) < 0);

        String reason = form.getReason() == null || form.getReason().isBlank() ? null : form.getReason().trim();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String iso = d.toString();
            blocked.removeIf(b -> b.getDate().equals(iso)); // re-blocking a day just updates its reason
            blocked.add(new BlockedDate(iso, reason));
        }
        if (blocked.size() > MAX_BLOCKED_DATES) {
            throw new ValidationException("You can have at most " + MAX_BLOCKED_DATES + " blocked days.");
        }
        schedule.setBlockedDates(blocked);
        repository.save(schedule);
    }

    public void unblockDate(String userId, String isoDate) {
        repository.findByUserId(userId).ifPresent(schedule -> {
            List<BlockedDate> blocked = new ArrayList<>(schedule.getBlockedDates());
            if (blocked.removeIf(b -> b.getDate().equals(isoDate))) {
                schedule.setBlockedDates(blocked);
                repository.save(schedule);
            }
        });
    }

    // ---------- Helpers ----------

    private static List<DaySchedule> defaultDays() {
        List<DaySchedule> days = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean weekday = day.getValue() <= DayOfWeek.FRIDAY.getValue();
            days.add(new DaySchedule(day, weekday, 540, 1020)); // 09:00 - 17:00
        }
        return days;
    }

    private DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(value);
        } catch (RuntimeException ex) {
            throw new ValidationException("The schedule contains an invalid day.");
        }
    }

    private Integer toMinutes(String hhmm) {
        if (hhmm == null || !hhmm.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return null;
        }
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }
}
