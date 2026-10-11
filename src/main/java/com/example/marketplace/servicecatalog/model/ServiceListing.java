package com.example.marketplace.servicecatalog.model;

import com.example.marketplace.common.enums.PriceType;
import com.example.marketplace.common.enums.ServiceStatus;
import java.math.BigDecimal;
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
 * A service a worker offers. Belongs to exactly one worker and one category.
 * workerId is the worker's USER id (the same id used for bookings and ownership checks).
 */
@Document(collection = "service_listings")
@CompoundIndex(name = "public_listing", def = "{'status': 1, 'workerApproved': 1, 'categoryId': 1, 'price': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceListing {

    @Id
    private String id;

    @Indexed
    private String workerId;

    private String categoryId;

    private String title;

    @Indexed(unique = true)
    private String slug;

    private String description;

    /** "What's included" bullet points. */
    @Builder.Default
    private List<String> includes = new ArrayList<>();

    /** Ordered photos; the first one is the primary photo. */
    @Builder.Default
    private List<ServiceImage> images = new ArrayList<>();

    private BigDecimal price;

    private PriceType priceType;

    private int durationMinutes;

    /** Free-text area label, e.g. "Mirpur and nearby". */
    private String serviceArea;

    /** Optional map position (GeoJSON: longitude, latitude). */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint geoLocation;

    private int serviceRadiusKm;

    @Builder.Default
    private ServiceStatus status = ServiceStatus.ACTIVE;

    /**
     * Denormalized copy of "the owning worker is APPROVED", so public search can filter with one
     * indexed query. Kept in sync by ServiceListingService and by the WorkerVerificationChangedEvent listener.
     */
    private boolean workerApproved;

    /** Maintained by the review module: sum of all ratings, count, and the derived average. */
    private int ratingSum;

    private double ratingAverage;

    private int ratingCount;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
