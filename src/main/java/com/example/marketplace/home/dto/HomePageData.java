package com.example.marketplace.home.dto;

import com.example.marketplace.review.dto.ReviewDto;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import java.util.List;
import lombok.Value;

/**
 * Read-only view model for the public homepage.
 * Categories, featured services, featured professionals and reviews are real data.
 * Only the statistics panel is still sample content.
 */
@Value
public class HomePageData {

    List<CategoryCard> categories;
    List<ServiceListingDto> featuredServices;
    List<WorkerProfileDto> featuredWorkers;
    List<ReviewDto> reviews;
    List<StatItem> stats;

    /** A category tile. A null slug means "link to the full category list". */
    @Value
    public static class CategoryCard {
        String name;
        String description;
        String icon;
        String tone;
        String slug;
    }

    @Value
    public static class StatItem {
        String value;
        String label;
    }
}
