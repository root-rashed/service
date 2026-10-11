package com.example.marketplace.security;

import com.example.marketplace.user.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Login brute-force protection.
 * Counts consecutive bad-credential failures atomically in MongoDB and applies a
 * temporary lock once the threshold is reached. Locked accounts are rejected by
 * AppUserDetails#isAccountNonLocked. Passwords are never logged or stored here.
 */
@Component
public class LoginAttemptService {

    private final MongoTemplate mongoTemplate;
    private final int maxAttempts;
    private final long lockMinutes;

    public LoginAttemptService(MongoTemplate mongoTemplate,
                               @Value("${app.security.max-login-attempts:5}") int maxAttempts,
                               @Value("${app.security.lock-minutes:15}") long lockMinutes) {
        this.mongoTemplate = mongoTemplate;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = normalize(event.getAuthentication().getName());
        if (email.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("email").is(email));
        User updated = mongoTemplate.findAndModify(
                query,
                new Update().inc("failedLoginAttempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                User.class);
        if (updated != null && updated.getFailedLoginAttempts() >= maxAttempts) {
            mongoTemplate.updateFirst(query,
                    new Update()
                            .set("failedLoginAttempts", 0)
                            .set("lockedUntil", Instant.now().plus(lockMinutes, ChronoUnit.MINUTES)),
                    User.class);
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String email = normalize(event.getAuthentication().getName());
        if (email.isEmpty()) {
            return;
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("email").is(email),
                new Criteria().orOperator(
                        Criteria.where("failedLoginAttempts").gt(0),
                        Criteria.where("lockedUntil").ne(null))));
        mongoTemplate.updateFirst(query,
                new Update().set("failedLoginAttempts", 0).unset("lockedUntil"),
                User.class);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
