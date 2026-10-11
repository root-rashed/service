package com.example.marketplace.chatbot.service;

import com.example.marketplace.chatbot.config.ChatbotProperties;
import com.example.marketplace.chatbot.dto.AssistantSearchResult;
import com.example.marketplace.chatbot.dto.AssistantServiceView;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.servicecatalog.dto.ServiceDetailView;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.dto.ServiceSearchCriteria;
import com.example.marketplace.servicecatalog.dto.ServiceSort;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import com.example.marketplace.servicecatalog.service.ServiceSearchService;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.worker.service.WorkerProfileService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The assistant's only window onto the marketplace.
 *
 * <p>Three rules govern everything here:
 *
 * <ul>
 *   <li>Every method delegates to the same public services the web pages use, so the assistant can
 *       never see, and therefore never reveal, anything a visitor could not already see.
 *   <li>No method queries MongoDB directly, so search behaviour, visibility rules and caching stay
 *       in one place instead of being duplicated for the model's benefit.
 *   <li>Nothing returns contact details or live availability. Both are answered by pointing at the
 *       service and booking pages, because quoting them from a language model is how wrong
 *       information gets published.
 * </ul>
 */
@Slf4j
@Component
public class MarketplaceAssistant {

    private final ServiceSearchService serviceSearchService;
    private final PublicServiceQueryService publicServiceQueryService;
    private final CategoryService categoryService;
    private final WorkerProfileService workerProfileService;
    private final ChatbotProperties properties;

    public MarketplaceAssistant(ServiceSearchService serviceSearchService,
                                PublicServiceQueryService publicServiceQueryService,
                                CategoryService categoryService,
                                WorkerProfileService workerProfileService,
                                ChatbotProperties properties) {
        this.serviceSearchService = serviceSearchService;
        this.publicServiceQueryService = publicServiceQueryService;
        this.categoryService = categoryService;
        this.workerProfileService = workerProfileService;
        this.properties = properties;
    }

    @Tool(description = """
            Search the marketplace catalogue for services. Use this whenever the customer names a
            service, a profession or a problem ("plumber", "deep cleaning", "math tutor").
            Pass an empty value for any filter the customer did not mention. Never guess a filter:
            an empty value searches everything rather than narrowing wrongly.""")
    public AssistantSearchResult searchServices(
            @ToolParam(description = "Service name, profession or problem, e.g. 'plumber'. Empty for any.")
            String keyword,
            @ToolParam(description = "Category slug from listCategories, e.g. 'home-cleaning'. Empty for any.")
            String categorySlug,
            @ToolParam(description = "Area or neighbourhood the customer is in, e.g. 'Mirpur'. Empty for any.")
            String area,
            @ToolParam(description = "Largest price the customer can pay in BDT. Empty for no limit.")
            Integer maxPrice) {

        ServiceSearchCriteria criteria = new ServiceSearchCriteria();
        criteria.setQ(blankToNull(keyword));
        criteria.setCategory(blankToNull(categorySlug));
        criteria.setLocation(blankToNull(area));
        criteria.setMaxPrice(maxPrice == null ? null : BigDecimal.valueOf(maxPrice));
        criteria.setSort(ServiceSort.RATING);
        criteria.setPage(0);

        var page = serviceSearchService.search(criteria);
        if (page.isEmpty()) {
            return AssistantSearchResult.none(
                    "No published service matched " + describe(keyword, categorySlug, area) + ".");
        }

        List<AssistantServiceView> matches = new ArrayList<>();
        for (ServiceListingDto service : page.getContent().stream().limit(properties.getMaxResults()).toList()) {
            matches.add(AssistantServiceView.of(service, professionalName(service.getWorkerId())));
        }
        String summary = "Found " + page.getTotalElements()
                + (page.getTotalElements() == 1 ? " matching service." : " matching services.");
        return new AssistantSearchResult(summary, matches, AssistantSearchResult.AVAILABILITY_NOTE);
    }

    @Tool(description = """
            Look up one service by its slug and return its price, duration, area and what the
            package includes. Use this after searchServices when the customer asks about a
            specific service by name.""")
    public AssistantSearchResult describeService(
            @ToolParam(description = "Slug of the service, as returned by searchServices.")
            String slug) {

        ServiceDetailView detail;
        try {
            detail = publicServiceQueryService.getDetail(blankToNull(slug));
        } catch (ResourceNotFoundException ex) {
            // An unknown or withdrawn slug is an ordinary outcome, not a failure: say so and stop.
            log.debug("Assistant asked for an unavailable service slug '{}'", slug);
            return AssistantSearchResult.none("No published service has the slug '" + slug + "'.");
        }

        List<String> includes = detail.getService().getIncludes();
        String price = AssistantServiceView.of(detail.getService()).price();
        String summary = detail.getService().getTitle() + " costs " + price
                + (includes.isEmpty() ? "." : " and includes " + String.join(", ", includes) + ".");

        return new AssistantSearchResult(summary,
                List.of(AssistantServiceView.of(detail.getService(), detail.getWorker().getName())),
                AssistantSearchResult.AVAILABILITY_NOTE);
    }

    @Tool(description = """
            List the service categories available on the marketplace, each with its slug. Use this
            to turn a customer's own word into a categorySlug for searchServices, or to answer
            "what kinds of services do you have".""")
    public List<String> listCategories() {
        return categoryService.getActiveCategories().stream()
                .map(category -> category.getName() + " (slug: " + category.getSlug() + ") - "
                        + oneLine(category.getDescription()))
                .toList();
    }

    // ---------- Helpers ----------

    /**
     * The public display name of a professional, or a neutral phrase when their profile is not
     * published. Returning a raw user id would be both unhelpful and a small leak.
     */
    private String professionalName(String workerUserId) {
        if (workerUserId == null) {
            return "not published";
        }
        return workerProfileService.getPublicByUserId(workerUserId)
                .map(profile -> profile.getName())
                .orElse("not published");
    }

    private static String oneLine(String text) {
        if (text == null || text.isBlank()) {
            return "no description";
        }
        return text.strip().replaceAll("\\s+", " ");
    }

    /** Names the filters that were actually applied, so a "nothing found" message is specific. */
    private static String describe(String keyword, String categorySlug, String area) {
        List<String> filters = new ArrayList<>();
        if (keyword != null) {
            filters.add("the keyword '" + keyword + '\'');
        }
        if (categorySlug != null) {
            filters.add("category '" + categorySlug + '\'');
        }
        if (area != null) {
            filters.add("area '" + area + '\'');
        }
        return filters.isEmpty() ? "any filter" : String.join(" and ", filters);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}