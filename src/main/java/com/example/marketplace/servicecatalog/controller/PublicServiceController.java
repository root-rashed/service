package com.example.marketplace.servicecatalog.controller;

import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.review.service.ReviewService;
import com.example.marketplace.schedule.service.WorkerScheduleService;
import com.example.marketplace.servicecatalog.dto.ServiceDetailView;
import com.example.marketplace.servicecatalog.dto.ServiceSearchCriteria;
import com.example.marketplace.servicecatalog.dto.ServiceSort;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import com.example.marketplace.servicecatalog.service.ServiceSearchService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Public service browsing and search. Invalid filter values (for example a non-numeric price) are
 * simply ignored; they never produce an error page.
 */
@Controller
public class PublicServiceController {

    private final ServiceSearchService serviceSearchService;
    private final PublicServiceQueryService publicServiceQueryService;
    private final CategoryService categoryService;
    private final WorkerScheduleService workerScheduleService;
    private final ReviewService reviewService;

    public PublicServiceController(ServiceSearchService serviceSearchService,
                                   PublicServiceQueryService publicServiceQueryService,
                                   CategoryService categoryService,
                                   WorkerScheduleService workerScheduleService,
                                   ReviewService reviewService) {
        this.serviceSearchService = serviceSearchService;
        this.publicServiceQueryService = publicServiceQueryService;
        this.categoryService = categoryService;
        this.workerScheduleService = workerScheduleService;
        this.reviewService = reviewService;
    }

    @GetMapping("/services")
    public String list(@ModelAttribute("criteria") ServiceSearchCriteria criteria,
                       BindingResult ignoredBindingErrors,
                       Model model) {
        model.addAttribute("results", serviceSearchService.search(criteria));
        model.addAttribute("categories", categoryService.getActiveCategories());
        model.addAttribute("sorts", ServiceSort.values());
        model.addAttribute("radii", List.of(5, 10, 25, 50));
        model.addAttribute("queryString", criteria.toQueryString());
        model.addAttribute("hasGeo", criteria.hasGeo());
        model.addAttribute("activeNav", "services");
        return "services/list";
    }

    @GetMapping("/services/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        ServiceDetailView view = publicServiceQueryService.getDetail(slug);
        model.addAttribute("service", view.getService());
        model.addAttribute("worker", view.getWorker());
        model.addAttribute("categorySlug", view.getCategorySlug());
        // Reviews are read fresh (a plain indexed query), so a new review shows up immediately.
        model.addAttribute("reviews", reviewService.getLatestForService(view.getService().getId(), 10));
        // Working hours are a plain read, never cached: workers can change them at any time.
        model.addAttribute("weeklyHours", workerScheduleService.getWeeklySummary(view.getService().getWorkerId()));
        model.addAttribute("activeNav", "services");
        return "services/detail";
    }

    /** The homepage search box posts here; /services is the single canonical listing. */
    @GetMapping("/search")
    public String search(@ModelAttribute("criteria") ServiceSearchCriteria criteria,
                         BindingResult ignoredBindingErrors) {
        String query = criteria.toQueryString();
        return "redirect:/services" + (query.isEmpty() ? "" : "?" + query);
    }
}
