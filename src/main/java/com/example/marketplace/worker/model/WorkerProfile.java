package com.example.marketplace.worker.model;

import com.example.marketplace.common.enums.WorkerVerificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Public-facing professional profile. One per worker account (userId is unique).
 * Only profiles with verificationStatus APPROVED are visible to the public.
 */
@Document(collection = "worker_profiles")
@CompoundIndex(name = "status_rating", def = "{'verificationStatus': 1, 'ratingAverage': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerProfile {

    @Id
    private String id;

    /** Owning user account. Always set server-side from the logged-in principal. */
    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String slug;

    /** Snapshot of the account name, kept here for fast listing and slug generation. */
    private String displayName;

    /** Profession, e.g. "Plumber". */
    private String headline;

    private String bio;

    private int experienceYears;

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    /** Main area, e.g. "Mirpur". */
    private String area;

    /** Free-text description of areas served. */
    private String serviceArea;

    /** Optional map position (GeoJSON: longitude, latitude). */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint geoLocation;

    @Builder.Default
    private WorkerVerificationStatus verificationStatus = WorkerVerificationStatus.PENDING;

    /** Maintained by the review module: sum of all ratings, count, and the derived average. */
    private int ratingSum;

    private double ratingAverage;

    private int ratingCount;

    /** Maintained by the booking module. */
    private int completedJobs;

    /** Maintained by the media module. */
    private String profileImage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
