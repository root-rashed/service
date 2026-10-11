package com.example.marketplace.worker.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.common.util.GeoUtil;
import com.example.marketplace.common.util.SlugUtil;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import com.example.marketplace.worker.dto.WorkerProfileForm;
import com.example.marketplace.worker.event.WorkerVerificationChangedEvent;
import com.example.marketplace.worker.model.WorkerProfile;
import com.example.marketplace.worker.repository.WorkerProfileRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

/**
 * Worker profile management and public profile reads.
 * Callers pass the user id taken from the authenticated principal, never from request input,
 * so a worker can only ever read or change their own profile.
 */
@Service
public class WorkerProfileService {

    private static final int PAGE_SIZE = 12;
    private static final int MAX_SKILLS = 10;
    private static final int MAX_SKILL_LENGTH = 30;

    private final WorkerProfileRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public WorkerProfileService(WorkerProfileRepository repository,
                                ApplicationEventPublisher eventPublisher,
                                UserRepository userRepository) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
    }

    // ---------- Owner (worker) operations ----------

    public Optional<WorkerProfileDto> getOwnProfile(String userId) {
        return repository.findByUserId(userId).map(WorkerProfileDto::from);
    }

    /** Returns a form pre-filled from the worker's existing profile, or an empty form. */
    public WorkerProfileForm getFormForUser(String userId) {
        WorkerProfileForm form = new WorkerProfileForm();
        repository.findByUserId(userId).ifPresent(p -> {
            form.setHeadline(p.getHeadline());
            form.setBio(p.getBio());
            form.setExperienceYears(p.getExperienceYears());
            form.setSkillsText(p.getSkills() == null ? "" : String.join(", ", p.getSkills()));
            form.setArea(p.getArea());
            form.setServiceArea(p.getServiceArea());
            if (p.getGeoLocation() != null) {
                form.setLongitude(p.getGeoLocation().getX());
                form.setLatitude(p.getGeoLocation().getY());
            }
        });
        return form;
    }

    /**
     * Creates the profile (status PENDING) or updates the existing one.
     * Verification status is never changed here; only admins can change it.
     */
    @CacheEvict(cacheNames = CacheNames.WORKER_PROFILES, allEntries = true)
    public WorkerProfileDto saveProfile(String userId, String displayName, WorkerProfileForm form) {
        List<String> skills = parseSkills(form.getSkillsText());
        GeoJsonPoint point = GeoUtil.toPoint(form.getLatitude(), form.getLongitude());

        WorkerProfile profile = repository.findByUserId(userId).orElseGet(() -> WorkerProfile.builder()
                .userId(userId)
                .slug(uniqueSlug(displayName))
                .verificationStatus(WorkerVerificationStatus.PENDING)
                .profileImage(accountPhoto(userId))
                .build());

        profile.setDisplayName(displayName);
        profile.setHeadline(form.getHeadline().trim());
        profile.setBio(form.getBio().trim());
        profile.setExperienceYears(form.getExperienceYears());
        profile.setSkills(skills);
        profile.setArea(form.getArea().trim());
        profile.setServiceArea(blankToNull(form.getServiceArea()));
        profile.setGeoLocation(point);

        WorkerProfile saved;
        try {
            saved = repository.save(profile);
        } catch (DuplicateKeyException ex) {
            throw new ValidationException("Could not save your profile. Please reload the page and try again.");
        }
        // Lets other modules re-sync anything derived from the worker's visibility.
        eventPublisher.publishEvent(new WorkerVerificationChangedEvent(userId, saved.getVerificationStatus()));
        return WorkerProfileDto.from(saved);
    }

    // ---------- Public reads ----------

    /** Only APPROVED profiles are public. Cached; evicted whenever a profile changes. */
    @Cacheable(cacheNames = CacheNames.WORKER_PROFILES, key = "'slug:' + #root.args[0]")
    public WorkerProfileDto getPublicBySlug(String slug) {
        return repository.findBySlugAndVerificationStatus(slug, WorkerVerificationStatus.APPROVED)
                .map(WorkerProfileDto::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Professional"));
    }

    public Page<WorkerProfileDto> getPublicProfiles(int page) {
        int safePage = Math.max(page, 0);
        Sort sort = Sort.by(
                Sort.Order.desc("ratingAverage"),
                Sort.Order.desc("completedJobs"),
                Sort.Order.asc("displayName"));
        return repository
                .findByVerificationStatus(WorkerVerificationStatus.APPROVED, PageRequest.of(safePage, PAGE_SIZE, sort))
                .map(WorkerProfileDto::from);
    }

    /** Public lookup by owner id (used by the service detail page). Only APPROVED profiles. */
    public Optional<WorkerProfileDto> getPublicByUserId(String userId) {
        return repository.findByUserIdAndVerificationStatus(userId, WorkerVerificationStatus.APPROVED)
                .map(WorkerProfileDto::from);
    }

    /** Top-rated approved professionals for the homepage. Cached; evicted on every profile change. */
    @Cacheable(cacheNames = CacheNames.WORKER_PROFILES, key = "'featured:' + #root.args[0]")
    public List<WorkerProfileDto> getFeatured(int limit) {
        Sort sort = Sort.by(
                Sort.Order.desc("ratingAverage"),
                Sort.Order.desc("completedJobs"),
                Sort.Order.asc("displayName"));
        return repository
                .findByVerificationStatus(WorkerVerificationStatus.APPROVED,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 12)), sort))
                .map(WorkerProfileDto::from)
                .getContent();
    }

    // ---------- Helpers ----------

    /** A photo uploaded before the profile existed is carried over to the new profile. */
    private String accountPhoto(String userId) {
        return userRepository.findById(userId).map(User::getProfileImage).orElse(null);
    }

    private List<String> parseSkills(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> skills = Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (skills.size() > MAX_SKILLS) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of("skillsText", "Please list at most " + MAX_SKILLS + " skills."));
        }
        if (skills.stream().anyMatch(s -> s.length() > MAX_SKILL_LENGTH)) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of("skillsText", "Each skill can be at most " + MAX_SKILL_LENGTH + " characters."));
        }
        return new ArrayList<>(skills);
    }

    private String uniqueSlug(String displayName) {
        String base = SlugUtil.toSlug(displayName);
        if (base.isEmpty()) {
            base = "professional";
        }
        String candidate = base;
        int suffix = 2;
        while (repository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
