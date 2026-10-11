package com.example.marketplace.user.model;

import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import java.time.Instant;
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
 * Account document shared by customers, workers and admins.
 * Email is stored trimmed and lower-cased (normalized by the service layer).
 * The password is only ever stored as a BCrypt hash.
 */
@Document(collection = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String phone;

    private String passwordHash;

    @Indexed
    private Role role;

    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    /** Relative path/key of the profile image (managed by the media module). */
    private String profileImage;

    /** Consecutive failed logins; reset on success or when the account locks. */
    private int failedLoginAttempts;

    /** Temporary login lock; null or a past instant means not locked. */
    private Instant lockedUntil;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
