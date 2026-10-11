package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminUserDto;
import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.common.exception.AccessForbiddenException;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.user.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Account administration: searching accounts and suspending or restoring them.
 *
 * Administrators are modelled as moderators, not account managers: password hashes and login
 * locks are never readable through this API, and an administrator can not suspend their own
 * account, which would leave the platform without an operator.
 */
@Service
public class AdminUserService {

    private static final int PAGE_SIZE = 15;
    private static final int MAX_KEYWORD_LENGTH = 60;

    private final MongoTemplate mongoTemplate;

    public AdminUserService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param keyword free text matched against name, email and phone
     * @param role    optional role filter; blank or unknown means all roles
     * @param status  optional status filter; blank or unknown means all statuses
     */
    public Page<AdminUserDto> search(String keyword, String role, String status, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id")));
        Query query = Query.query(buildCriteria(keyword, role, status));
        long total = mongoTemplate.count(query, User.class);
        query.skip((long) pageable.getPageNumber() * PAGE_SIZE).limit(PAGE_SIZE);

        List<AdminUserDto> content = mongoTemplate.find(query, User.class).stream()
                .map(u -> AdminUserDto.from(u, u.getLockedUntil() != null))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    public AdminUserDto get(String userId) {
        User user = find(userId);
        return AdminUserDto.from(user, user.getLockedUntil() != null);
    }

    /** Restores a suspended account and clears any login lock. */
    public void activate(String userId) {
        User user = find(userId);
        if (user.getStatus() == UserStatus.SUSPENDED) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(userId)),
                    new Update().set("status", UserStatus.ACTIVE)
                            .set("failedLoginAttempts", 0)
                            .unset("lockedUntil"),
                    User.class);
        }
    }

    /** Suspends an account. Administrator accounts are protected. */
    public void suspend(String userId) {
        User user = find(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new AccessForbiddenException("Administrator accounts can't be suspended.");
        }
        if (user.getStatus() != UserStatus.SUSPENDED) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(userId)),
                    new Update().set("status", UserStatus.SUSPENDED),
                    User.class);
        }
    }

    // ---------- Internals ----------

    private User find(String userId) {
        User user = mongoTemplate.findById(userId, User.class);
        if (user == null) {
            throw ResourceNotFoundException.of("Account");
        }
        return user;
    }

    private Criteria buildCriteria(String keyword, String role, String status) {
        List<Criteria> filters = new ArrayList<>(2);
        Role parsedRole = parseEnum(Role.class, role);
        if (parsedRole != null) {
            filters.add(Criteria.where("role").is(parsedRole));
        }
        UserStatus parsedStatus = parseEnum(UserStatus.class, status);
        if (parsedStatus != null) {
            filters.add(Criteria.where("status").is(parsedStatus));
        }

        String term = trim(keyword);
        if (!term.isEmpty()) {
            // Pattern.quote keeps user input out of the regex engine entirely.
            String regex = Pattern.quote(term);
            Criteria keywordMatch = new Criteria().orOperator(
                    Criteria.where("name").regex(regex, "i"),
                    Criteria.where("email").regex(regex, "i"),
                    Criteria.where("phone").regex(regex, "i"));
            filters.add(keywordMatch);
        }

        if (filters.isEmpty()) {
            return new Criteria();
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new Criteria().andOperator(filters.toArray(new Criteria[0]));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // An unknown filter in the query string is ignored rather than treated as an error.
            return null;
        }
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
    }
}