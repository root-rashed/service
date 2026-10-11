package com.example.marketplace.home.service;

import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.home.dto.HomePageData;
import com.example.marketplace.home.dto.HomePageData.CategoryCard;
import com.example.marketplace.home.dto.HomePageData.StatItem;
import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import com.example.marketplace.worker.service.WorkerProfileService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Supplies homepage content.
 *
 * REAL (cached through Spring Cache): categories, featured services, featured professionals, reviews.
 * DEMO: only the statistics panel.
 */
@Service
public class HomePageService {

    private static final int FEATURED_SERVICES = 5;
    private static final int FEATURED_WORKERS = 4;
    private static final int REVIEWS = 3;

    private final CategoryService categoryService;
    private final PublicServiceQueryService publicServiceQueryService;
    private final WorkerProfileService workerProfileService;
    private final ReviewService reviewService;

    public HomePageService(CategoryService categoryService,
                           PublicServiceQueryService publicServiceQueryService,
                           WorkerProfileService workerProfileService,
                           ReviewService reviewService) {
        this.categoryService = categoryService;
        this.publicServiceQueryService = publicServiceQueryService;
        this.workerProfileService = workerProfileService;
        this.reviewService = reviewService;
    }

    /** True while any part of the page is still sample content. */
    public boolean isDemoContent() {
        return true;
    }

    public HomePageData getHomePage() {
        return new HomePageData(
                categories(),
                publicServiceQueryService.getPopular(FEATURED_SERVICES),
                workerProfileService.getFeatured(FEATURED_WORKERS),
                reviewService.getLatestForHome(REVIEWS),
                stats());
    }

    private List<CategoryCard> categories() {
        List<CategoryCard> cards = new ArrayList<>(categoryService.getActiveCategories().stream()
                .limit(7)
                .map(c -> new CategoryCard(c.getName(), c.getDescription(), c.getIcon(), c.getTone(), c.getSlug()))
                .toList());
        cards.add(new CategoryCard("More", "Explore all", "bi-three-dots", "sky", null));
        return cards;
    }

    private List<StatItem> stats() {
        return List.of(
                new StatItem("25K+", "Happy customers"),
                new StatItem("1.2K+", "Verified professionals"),
                new StatItem("4.8", "Average rating"));
    }
}
