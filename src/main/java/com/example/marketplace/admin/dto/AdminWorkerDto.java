package com.example.marketplace.admin.dto;

import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.worker.model.WorkerProfile;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/** Read-only professional-profile view for the verification queue and the worker directory. */
@Value
public class AdminWorkerDto {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    String userId;
    String slug;
    String displayName;
    String email;
    String phone;
    String headline;
    String area;
    String serviceArea;
    int experienceYears;
    List<String> skills;
    WorkerVerificationStatus verificationStatus;
    double rating;
    int reviewCount;
    int completedJobs;
    int serviceCount;
    String profileImageUrl;
    String createdOn;

    /** @param serviceCount number of listings this professional owns, shown in the queue. */
    public static AdminWorkerDto from(WorkerProfile p, String email, String phone, int serviceCount) {
        return new AdminWorkerDto(
                p.getId(),
                p.getUserId(),
                p.getSlug(),
                p.getDisplayName(),
                email,
                phone,
                p.getHeadline(),
                p.getArea(),
                p.getServiceArea(),
                p.getExperienceYears(),
                p.getSkills() == null ? List.of() : List.copyOf(p.getSkills()),
                p.getVerificationStatus(),
                p.getRatingAverage(),
                p.getRatingCount(),
                p.getCompletedJobs(),
                serviceCount,
                MediaUrls.publicUrl(p.getProfileImage()),
                p.getCreatedAt() == null ? "" : DATE.format(p.getCreatedAt()));
    }

    public String statusLabel() {
        return switch (verificationStatus) {
            case PENDING -> "Awaiting review";
            case APPROVED -> "Approved";
            case REJECTED -> "Not approved";
            case SUSPENDED -> "Suspended";
        };
    }

    public String statusTone() {
        return switch (verificationStatus) {
            case PENDING -> "amber";
            case APPROVED -> "green";
            case REJECTED, SUSPENDED -> "red";
        };
    }

    /** The list of decisions still worth showing as buttons on the queue. */
    public boolean isActionable() {
        return verificationStatus == WorkerVerificationStatus.PENDING;
    }
}