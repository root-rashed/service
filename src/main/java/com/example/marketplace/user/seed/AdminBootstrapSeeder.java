package com.example.marketplace.user.seed;

import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the very first administrator so the admin area is reachable on a fresh database.
 *
 * <p>Off unless {@code app.admin.email} and {@code app.admin.password} are both supplied, so no
 * default password is ever shipped. The account is created only when no administrator exists at
 * all, and an existing account with the same email is never modified or promoted — this seeder
 * bootstraps an empty database, it is not a way to escalate privileges.
 */
@Slf4j
@Component
public class AdminBootstrapSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String rawPassword;
    private final String displayName;

    public AdminBootstrapSeeder(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:}") String email,
                                @Value("${app.admin.password:}") String rawPassword,
                                @Value("${app.admin.name:Administrator}") String displayName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.rawPassword = rawPassword == null ? "" : rawPassword;
        this.displayName = displayName == null || displayName.isBlank() ? "Administrator" : displayName.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(rawPassword)) {
            log.info("No administrator configured: set ADMIN_EMAIL and ADMIN_PASSWORD to create the first one.");
            return;
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("An administrator already exists, leaving accounts untouched.");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.warn("{} already has an account but no ADMIN role exists, so it was NOT promoted. "
                    + "Grant the role manually if that is intended.", email);
            return;
        }

        User admin = User.builder()
                .name(displayName)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.ADMIN)
                // Active immediately: an administrator has no email verification to wait for.
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);

        log.info("Created the first administrator account for {}. Change this password before going live.", email);
    }
}