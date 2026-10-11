package com.example.marketplace.servicecatalog.dto;

import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.common.enums.PriceType;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import java.math.BigDecimal;
import java.util.List;
import lombok.Value;

/**
 * Read-only service view. Property names (title, slug, categoryName, rating, reviewCount, price,
 * priceUnit, location, badge, badgeTone, icon, tone) match the shared serviceCard fragment.
 */
@Value
public class ServiceListingDto {

    String id;
    String slug;
    String workerId;
    String categoryId;
    String categoryName;
    String title;
    String description;
    List<String> includes;
    BigDecimal price;
    PriceType priceType;
    String priceUnit;
    int durationMinutes;
    String location;
    int serviceRadiusKm;
    Double latitude;
    Double longitude;
    ServiceStatus status;
    double rating;
    int reviewCount;
    String badge;
    String badgeTone;
    String icon;
    String tone;
    List<ServiceImageDto> images;
    /** URL of the first image, or null when the service has no photos yet. */
    String primaryImageUrl;

    /** Copy with a different location label (used to append the distance in search results). */
    public ServiceListingDto withLocation(String newLocation) {
        return new ServiceListingDto(id, slug, workerId, categoryId, categoryName, title, description, includes,
                price, priceType, priceUnit, durationMinutes, newLocation, serviceRadiusKm, latitude, longitude,
                status, rating, reviewCount, badge, badgeTone, icon, tone, images, primaryImageUrl);
    }

    public boolean isActive() {
        return status == ServiceStatus.ACTIVE;
    }

    public String getDurationLabel() {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        if (minutes == 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return hours + " h " + minutes + " min";
    }

    /** @param category may be null if the category no longer exists. */
    public static ServiceListingDto from(ServiceListing s, CategoryDto category) {
        List<ServiceImageDto> imageDtos = s.getImages() == null ? List.of() : s.getImages().stream()
                .map(i -> new ServiceImageDto(i.getId(), MediaUrls.publicUrl(i.getKey())))
                .toList();
        return new ServiceListingDto(
                s.getId(),
                s.getSlug(),
                s.getWorkerId(),
                s.getCategoryId(),
                category == null ? "Uncategorized" : category.getName(),
                s.getTitle(),
                s.getDescription(),
                s.getIncludes() == null ? List.of() : List.copyOf(s.getIncludes()),
                s.getPrice(),
                s.getPriceType(),
                s.getPriceType() == null ? "" : s.getPriceType().getUnitLabel(),
                s.getDurationMinutes(),
                s.getServiceArea(),
                s.getServiceRadiusKm(),
                s.getGeoLocation() == null ? null : s.getGeoLocation().getY(),
                s.getGeoLocation() == null ? null : s.getGeoLocation().getX(),
                s.getStatus(),
                s.getRatingAverage(),
                s.getRatingCount(),
                null,
                null,
                category == null ? "bi-grid" : category.getIcon(),
                category == null ? "orange" : category.getTone(),
                imageDtos,
                imageDtos.isEmpty() ? null : imageDtos.get(0).getUrl());
    }
}
