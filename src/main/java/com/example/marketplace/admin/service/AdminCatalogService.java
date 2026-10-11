package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminServiceDto;
import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.category.model.Category;
import com.example.marketplace.category.repository.CategoryRepository;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.worker.model.WorkerProfile;
import com.example.marketplace.worker.repository.WorkerProfileRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Service moderation. Administrators can take a single listing down (SUSPENDED) and put it
 * back (ACTIVE). This is deliberately separate from a professional's own ACTIVE/INACTIVE
 * switch: suspending hides the listing from the public catalog without the professional
 * losing ownership of it.
 */
@Service
public class AdminCatalogService {

    private static final int PAGE_SIZE = 15;
    private static final int MAX_KEYWORD_LENGTH = 60;

    private final MongoTemplate mongoTemplate;
    private final CategoryRepository categoryRepository;
    private final WorkerProfileRepository workerProfileRepository;

    public AdminCatalogService(MongoTemplate mongoTemplate,
                               CategoryRepository categoryRepository,
                               WorkerProfileRepository workerProfileRepository) {
        this.mongoTemplate = mongoTemplate;
        this.categoryRepository = categoryRepository;
        this.workerProfileRepository = workerProfileRepository;
    }

    /**
     * @param status optional listing-status filter; blank or unknown means all statuses
     * @param query  optional free text matched against the title and the service area
     */
    public Page<AdminServiceDto> search(String status, String query, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id")));
        Query mongoQuery = Query.query(buildCriteria(status, query));
        long total = mongoTemplate.count(mongoQuery, ServiceListing.class);
        mongoQuery.skip((long) pageable.getPageNumber() * PAGE_SIZE).limit(PAGE_SIZE);

        List<ServiceListing> listings = mongoTemplate.find(mongoQuery, ServiceListing.class);
        return new PageImpl<>(decorate(listings), pageable, total);
    }

    /**
     * Every listing owned by one professional, newest first. Used on the moderation detail page,
     * where the administrator needs to see what a profile actually publishes.
     */
    public List<AdminServiceDto> getByWorker(String workerId) {
        Query mongoQuery = Query.query(Criteria.where("workerId").is(workerId))
                .with(Sort.by(Sort.Order.desc("createdAt")));
        return decorate(mongoTemplate.find(mongoQuery, ServiceListing.class));
    }

    public AdminServiceDto get(String serviceId) {
        return decorate(List.of(find(serviceId))).get(0);
    }

    /** Catalog-wide counts per status, for the moderation landing page. */
    public Map<ServiceStatus, Long> countsByStatus() {
        Map<ServiceStatus, Long> counts = new LinkedHashMap<>();
        Arrays.stream(ServiceStatus.values())
                .forEach(status -> counts.put(status,
                        mongoTemplate.count(Query.query(Criteria.where("status").is(status)), ServiceListing.class)));
        return counts;
    }

    /** Statuses in declaration order, so the template can iterate them without sorting. */
    public List<ServiceStatus> statuses() {
        return Arrays.asList(ServiceStatus.values());
    }

    /** Takes a listing out of the public catalog. */
    @CacheEvict(cacheNames = {CacheNames.SERVICE_DETAILS, CacheNames.POPULAR_SERVICES}, allEntries = true)
    public void suspend(String serviceId) {
        setStatus(serviceId, ServiceStatus.SUSPENDED);
    }

    /** Puts a listing back into the catalog; it is only visible once its professional is approved. */
    @CacheEvict(cacheNames = {CacheNames.SERVICE_DETAILS, CacheNames.POPULAR_SERVICES}, allEntries = true)
    public void reinstate(String serviceId) {
        setStatus(serviceId, ServiceStatus.ACTIVE);
    }

    // ---------- Internals ----------

    private ServiceListing find(String serviceId) {
        ServiceListing listing = mongoTemplate.findById(serviceId, ServiceListing.class);
        if (listing == null) {
            throw ResourceNotFoundException.of("Service");
        }
        return listing;
    }

    private void setStatus(String serviceId, ServiceStatus status) {
        ServiceListing listing = find(serviceId);
        if (listing.getStatus() == status) {
            throw new ValidationException(
                    "This service is already " + status.name().toLowerCase(Locale.ROOT) + ".");
        }
        listing.setStatus(status);
        mongoTemplate.save(listing);
    }

    /**
     * Resolves the category and owner names for the listings on this page with one query each,
     * instead of a lookup per row while the template renders.
     */
    private List<AdminServiceDto> decorate(List<ServiceListing> listings) {
        if (listings.isEmpty()) {
            return List.of();
        }
        Map<String, String> categoryNames = categoryRepository.findAllById(
                        listings.stream().map(ServiceListing::getCategoryId)
                                .filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        Map<String, WorkerProfile> workers = workerProfileRepository.findAllById(
                        listings.stream().map(ServiceListing::getWorkerId)
                                .filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(WorkerProfile::getUserId, Function.identity(), (a, b) -> a));

        return listings.stream().map(s -> {
            WorkerProfile worker = workers.get(s.getWorkerId());
            return AdminServiceDto.from(s,
                    categoryNames.get(s.getCategoryId()),
                    worker == null ? null : worker.getDisplayName(),
                    worker == null ? null : worker.getSlug());
        }).toList();
    }

    private Criteria buildCriteria(String status, String query) {
        List<Criteria> filters = new ArrayList<>(2);
        ServiceStatus parsed = parseStatus(status);
        if (parsed != null) {
            filters.add(Criteria.where("status").is(parsed));
        }
        String term = trim(query);
        if (!term.isEmpty()) {
            // Pattern.quote keeps user input out of the regex engine entirely.
            String regex = Pattern.quote(term);
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("serviceArea").regex(regex, "i")));
        }
        if (filters.isEmpty()) {
            return new Criteria();
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return new Criteria().andOperator(filters.toArray(new Criteria[0]));
    }

    private ServiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ServiceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // An unknown filter in the query string is ignored rather than treated as an error.
            return null;
        }
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
    }
}
