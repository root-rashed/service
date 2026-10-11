package com.example.marketplace.admin.dto;

import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.model.Booking;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/**
 * One entry of a booking's audit trail, rendered on the admin booking detail page.
 *
 * The raw {@code Booking.StatusChange} stays inside the model: the view gets a formatted
 * timestamp and a readable actor instead of an Instant and a bare string.
 */
@Value
public class AdminStatusChangeDto {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String statusLabel;
    String statusTone;
    String whenLabel;
    String actor;

    public static AdminStatusChangeDto from(Booking.StatusChange change) {
        Booking.StatusChange safe = change == null ? new Booking.StatusChange() : change;
        return new AdminStatusChangeDto(
                safe.getStatus() == null ? "Unknown" : BookingDto.statusLabel(safe.getStatus()),
                safe.getStatus() == null ? "amber" : BookingDto.statusTone(safe.getStatus()),
                safe.getAt() == null ? "" : WHEN.format(safe.getAt()),
                actorLabel(safe.getBy()));
    }

    public static List<AdminStatusChangeDto> from(List<Booking.StatusChange> history) {
        return history == null ? List.of() : history.stream().map(AdminStatusChangeDto::from).toList();
    }

    /** Turns the stored actor code into something an administrator can read at a glance. */
    private static String actorLabel(String by) {
        if (by == null || by.isBlank()) {
            return "System";
        }
        return switch (by.trim().toUpperCase(Locale.ROOT)) {
            case "CUSTOMER" -> "Customer";
            case "WORKER" -> "Professional";
            case "ADMIN" -> "Administrator";
            default -> "System";
        };
    }
}
