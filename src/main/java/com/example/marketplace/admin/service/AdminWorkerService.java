package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminWorkerDto;
import com.example.marketplace.admin.event.WorkerModerationEvent;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import com.example.marketplace.worker.event.WorkerVerificationChangedEvent;
import com.example.marketplace.worker.model.WorkerProfile;
import com.example.marketplace.worker.repository.WorkerProfileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Worker-profile moderation: the verification queue and the directory of all professionals.
 *
 * A decision only changes {@code verificationStatus}. Publishing
 * {@code WorkerVerificationChangedEvent} is what makes the professional's listings visible or
 * hidden and clears the derived caches, so this class never writes service documents itself.
 */
@Service
public class AdminWorkerService {

    private static final int PAGE_SIZE = 12;
    private static final int MAX_KEYWORD_LENGTH = 60;
    private static final int MAX_NOTE_LENGTH = 300;

    private final MongoTemplate mongoTemplate;
    private final WorkerProfileRepository workerProfileRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminWorkerService(MongoTemplate mongoTemplate,
                              WorkerProfileRepository workerProfileRepository,
                              UserRepository userRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.mongoTemplate = mongoTemplate;
        this.workerProfileRepository = workerProfileRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    // ---------- Reads ----------

    /**
     * @param status optional verification filter; blank or unknown means all profiles
     * @param query  optional free text matched against display name, headline, area and skills
     */
    public Page<AdminWorkerDto> search(String status, String query, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.asc("verificationStatus"),
                        Sort.Order.desc("ratingAverage"),
                        Sort.Order.asc("displayName")));
        Criteria criteria = buildCriteria(status, query);
        Query mongoQuery = Query.query(criteria);
        long total = mongoTemplate.count(mongoQuery, WorkerProfile.class);
        mongoQuery.skip((long) pageable.getPageNumber() * PAGE_SIZE).limit(PAGE_SIZE);

        return decorate(mongoTemplate.find(mongoQuery, WorkerProfile.class), pageable, total);
    }

    /** Profiles waiting for a decision, oldest request first so nobody is starved. */
    public Page<AdminWorkerDto> getVerificationQueue(int page) {
        return search(WorkerVerificationStatus.PENDING.name(), null, page);
    }

    public AdminWorkerDto get(String profileId) {
        WorkerProfile profile = workerProfileRepository.findById(profileId)
                .orElseThrow(() -> ResourceNotFoundException.of("Professional"));
        return decorate(List.of(profile), PageRequest.of(0, 1), 1).getContent().get(0);
    }

    // ---------- Moderation ----------

    /** Approves the profile: the professional becomes searchable and their listings go live. */
    public AdminWorkerDto approve(String profileId, String note) {
        return decide(profileId, WorkerVerificationStatus.APPROVED, note);
    }

    /** Marks the profile as not approved; an optional note is shown to the professional. */
    public AdminWorkerDto reject(String profileId, String note) {
        return decide(profileId, WorkerVerificationStatus.REJECTED, note);
    }

    /** Hides an already approved professional again. */
    public AdminWorkerDto suspend(String profileId, String note) {
        return decide(profileId, WorkerVerificationStatus.SUSPENDED, note);
    }

    /** Puts a rejected or suspended profile back into the review queue. */
    public AdminWorkerDto restore(String profileId, String note) {
        return decide(profileId, WorkerVerificationStatus.PENDING, note);
    }

    // ---------- Internals ----------

    private AdminWorkerDto decide(String profileId, WorkerVerificationStatus target, String note) {
        WorkerProfile profile = workerProfileRepository.findById(profileId)
                .orElseThrow(() -> ResourceNotFoundException.of("Professional"));
        if (profile.getVerificationStatus() == target) {
            throw new ValidationException(
                    "This profile is already " + target.name().toLowerCase(Locale.ROOT) + ".");
        }
        String cleanNote = cleanNote(note);

        profile.setVerificationStatus(target);
        WorkerProfile saved = workerProfileRepository.save(profile);

        // Flips workerApproved on the listings and clears the derived caches.
        eventPublisher.publishEvent(new WorkerVerificationChangedEvent(saved.getUserId(), target));
        // Tells the professional what happened, with the reason they were given.
        eventPublisher.publishEvent(new WorkerModerationEvent(saved.getUserId(), target, cleanNote));

        return decorate(List.of(saved), PageRequest.of(0, 1), 1).getContent().get(0);
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > MAX_NOTE_LENGTH ? trimmed.substring(0, MAX_NOTE_LENGTH) : trimmed;
    }

    private Criteria buildCriteria(String status, String query) {
        List<Criteria> filters = new ArrayList<>(2);
        WorkerVerificationStatus parsed = parseStatus(status);
        if (parsed != null) {
            filters.add(Criteria.where("verificationStatus").is(parsed));
        }
        String term = trim(query);
        if (!term.isEmpty()) {
            // Pattern.quote keeps user input out of the regex engine entirely.
            String regex = Pattern.quote(term);
            filters.add(new Criteria().orOperator(
                    Criteria.where("displayName").regex(regex, "i"),
                    Criteria.where("headline").regex(regex, "i"),
                    Criteria.where("area").regex(regex, "i"),
                    Criteria.where("skills").regex(regex, "i")));
        }
        if (filters.isEmpty()) {
            return new Criteria();
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new Criteria().andOperator(filters.toArray(new Criteria[0]));
    }

    private WorkerVerificationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkerVerificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // An unknown filter in the query string is ignored rather than treated as an error.
            return null;
        }
    }

    /**
     * Joins the owning account and the listing count onto the profiles on this page, with one
     * query each instead of a lookup per row while rendering.
     */
    private Page<AdminWorkerDto> decorate(List<WorkerProfile> profiles, PageRequest pageable, long total) {
        Map<String, User> users = userRepository.findAllById(
                        profiles.stream().map(WorkerProfile::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, Long> listingCounts = countListingsPerWorker(profiles);

        List<AdminWorkerDto> content = profiles.stream()
                .map(p -> {
                    User user = users.get(p.getUserId());
                    return AdminWorkerDto.from(p,
                            user == null ? null : user.getEmail(),
                            user == null ? null : user.getPhone(),
                            listingCounts.getOrDefault(p.getUserId(), 0L).intValue());
                })
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    /** Listing count per professional, aggregated with a single query over the ids on this page. */
    private Map<String, Long> countListingsPerWorker(List<WorkerProfile> profiles) {
        List<String> workerIds = profiles.stream().map(WorkerProfile::getUserId).distinct().toList();
        if (workerIds.isEmpty()) {
            return Map.of();
        }
        AggregationResults<Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("workerId").in(workerIds)),
                        Aggregation.group("workerId").count().as("total")),
                ServiceListing.class, Document.class);
        Map<String, Long> counts = new HashMap<>();
        for (Document row : results.getMappedResults()) {
            counts.put(row.getString("_id"), ((Number) row.get("total")).longValue());
        }
        return counts;
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
    }
}