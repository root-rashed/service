package com.example.marketplace.admin.dto;

import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.user.model.User;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Value;

/**
 * Read-only account view for the administration area. Never carries the password hash,
 * the login-lock timestamp or any other authentication secret.
 */
@Value
public class AdminUserDto {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    String name;
    String email;
    String phone;
    Role role;
    UserStatus status;
    String profileImageUrl;
    /** "12 Mar 2026", or an empty string for records without a creation timestamp. */
    String registeredOn;
    int failedLoginAttempts;
    boolean locked;

    public static AdminUserDto from(User u, boolean locked) {
        return new AdminUserDto(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getPhone(),
                u.getRole(),
                u.getStatus(),
                MediaUrls.publicUrl(u.getProfileImage()),
                u.getCreatedAt() == null ? "" : DATE.format(u.getCreatedAt()),
                u.getFailedLoginAttempts(),
                locked);
    }

    public String initials() {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public String statusLabel() {
        return switch (status == null ? UserStatus.ACTIVE : status) {
            case PENDING_VERIFICATION -> "Not verified";
            case ACTIVE -> "Active";
            case SUSPENDED -> "Suspended";
        };
    }

    public String statusTone() {
        return switch (status == null ? UserStatus.ACTIVE : status) {
            case PENDING_VERIFICATION -> "amber";
            case ACTIVE -> "green";
            case SUSPENDED -> "red";
        };
    }
}