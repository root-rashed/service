package com.example.marketplace.admin.dto;

import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.Value;

/**
 * Moderation view of a service listing: the public fields plus everything an administrator needs
 * to make a call — who owns it, which category it sits in, and whether the owner is approved.
 *
 * A listing is only publicly visible when both {@code status == ACTIVE} and
 * {@code workerApproved} hold, which is why {@link #isPubliclyVisible()} needs both and why the
 * moderation table shows them as two separate columns.
 */
@Value
public class AdminServiceDto {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    String id;
    String title;
    String slug;
    String categoryId;
    String categoryName;
    String workerId;
    String workerName;
    String workerSlug;
    BigDecimal price;
    String priceLabel;
    String serviceArea;
    ServiceStatus status;
    boolean workerApproved;
    double rating;
    int reviewCount;
    String primaryImageUrl;
    String createdOn;

    /**
     * @param categoryName resolved category name, or "Uncategorized" when the category was deleted
     * @param workerName   resolved owner name, or "Removed professional" when the profile was deleted
     */
    public static AdminServiceDto from(ServiceListing s, String categoryName, String workerName, String workerSlug) {
        return new AdminServiceDto(
                s.getId(),
                s.getTitle(),
                s.getSlug(),
                s.getCategoryId(),
                categoryName == null ? "Uncategorized" : categoryName,
                s.getWorkerId(),
                workerName == null ? "Removed professional" : workerName,
                workerSlug,
                s.getPrice(),
                s.getPriceType() == null ? "" : s.getPriceType().getLabel(),
                s.getServiceArea(),
                s.getStatus(),
                s.isWorkerApproved(),
                s.getRatingAverage(),
                s.getRatingCount(),
                firstImage(s),
                s.getCreatedAt() == null ? "" : DATE.format(s.getCreatedAt()));
    }

    /** A listing reaches the public catalog only if it is active *and* its owner is approved. */
    public boolean isPubliclyVisible() {
        return status == ServiceStatus.ACTIVE && workerApproved;
    }

    public String statusLabel() {
        return switch (status == null ? ServiceStatus.ACTIVE : status) {
            case ACTIVE -> "Live";
            case INACTIVE -> "Paused by pro";
            case SUSPENDED -> "Suspended";
        };
    }

    public String statusTone() {
        return switch (status == null ? ServiceStatus.ACTIVE : status) {
            case ACTIVE -> "green";
            case INACTIVE -> "blue";
            case SUSPENDED -> "red";
        };
    }

    public String visibilityLabel() {
        if (!isPubliclyVisible()) {
            return "Hidden";
        }
        return "Public";
    }

    public String visibilityTone() {
        return isPubliclyVisible() ? "green" : "amber";
    }

    public boolean isSuspended() {
        return status == ServiceStatus.SUSPENDED;
    }

    private static String firstImage(ServiceListing s) {
        if (s.getImages() == null || s.getImages().isEmpty()) {
            return null;
        }
        return MediaUrls.publicUrl(s.getImages().get(0).getKey());
    }
}
