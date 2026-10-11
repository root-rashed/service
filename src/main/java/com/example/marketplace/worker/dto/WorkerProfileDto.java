package com.example.marketplace.worker.dto;

import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.worker.model.WorkerProfile;
import java.util.List;
import lombok.Value;

/**
 * Read-only worker view for pages (and for caching).
 * Property names (name, profession, location, rating, completedJobs, tone, slug) are
 * deliberately compatible with the shared workerCard fragment.
 */
@Value
public class WorkerProfileDto {

    private static final String[] TONES = {"blue", "pink", "indigo", "purple", "green", "orange", "rose", "sky"};

    String id;
    String slug;
    String name;
    String profession;
    String bio;
    int experienceYears;
    List<String> skills;
    String location;
    String serviceArea;
    double rating;
    int reviewCount;
    int completedJobs;
    WorkerVerificationStatus verificationStatus;
    String profileImage;
    String tone;
    /** The worker's user id (used to look up their reviews and bookings; never shown on pages). */
    String userId;

    /** Public URL of the profile photo, or null if there is none. */
    public String getProfileImageUrl() {
        return MediaUrls.publicUrl(profileImage);
    }

    public boolean isVerified() {
        return verificationStatus == WorkerVerificationStatus.APPROVED;
    }

    public static WorkerProfileDto from(WorkerProfile p) {
        String key = p.getSlug() == null ? "" : p.getSlug();
        return new WorkerProfileDto(
                p.getId(),
                p.getSlug(),
                p.getDisplayName(),
                p.getHeadline(),
                p.getBio(),
                p.getExperienceYears(),
                p.getSkills() == null ? List.of() : List.copyOf(p.getSkills()),
                p.getArea(),
                p.getServiceArea(),
                p.getRatingAverage(),
                p.getRatingCount(),
                p.getCompletedJobs(),
                p.getVerificationStatus(),
                p.getProfileImage(),
                TONES[Math.floorMod(key.hashCode(), TONES.length)],
                p.getUserId());
    }
}
