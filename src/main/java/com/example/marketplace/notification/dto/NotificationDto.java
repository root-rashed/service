package com.example.marketplace.notification.dto;

import com.example.marketplace.common.enums.NotificationType;
import com.example.marketplace.notification.model.Notification;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.Value;

/**
 * Read-only notification view. The icon and tone come from the type, so templates never
 * hard-code presentation per message.
 */
@Value
public class NotificationDto {

    private static final DateTimeFormatter ABSOLUTE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    NotificationType type;
    String title;
    String message;
    String link;
    String bookingId;
    boolean read;
    String icon;
    String tone;
    /** Human-friendly age such as "3 hours ago". */
    String ageLabel;

    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getLink(),
                n.getBookingId(),
                n.isRead(),
                n.getType() == null ? "bi-bell" : n.getType().getIcon(),
                n.getType() == null ? "blue" : n.getType().getTone(),
                age(n.getCreatedAt()));
    }

    private static String age(Instant createdAt) {
        if (createdAt == null) {
            return ABSOLUTE.format(Instant.now());
        }
        long minutes = ChronoUnit.MINUTES.between(createdAt, Instant.now());
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        if (days <= 7) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        return ABSOLUTE.format(createdAt);
    }
}
