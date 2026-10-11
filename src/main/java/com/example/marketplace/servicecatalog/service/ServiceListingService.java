package com.example.marketplace.servicecatalog.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.exception.AccessForbiddenException;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.common.util.GeoUtil;
import com.example.marketplace.common.util.SlugUtil;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.dto.ServiceListingForm;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.servicecatalog.repository.ServiceListingRepository;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import com.example.marketplace.worker.service.WorkerProfileService;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Worker-side service management.
 * Every method takes the worker's user id from the authenticated principal and scopes all
 * lookups by it (findByIdAndWorkerId), so changing an id in a URL can never reach another worker's data.
 */
@Service
public class ServiceListingService {

    private static final int MAX_SERVICES_PER_WORKER = 30;
    private static final int MAX_INCLUDES = 10;
    private static final int MAX_INCLUDE_LENGTH = 100;

    private final ServiceListingRepository repository;
    private final CategoryService categoryService;
    private final WorkerProfileService workerProfileService;

    public ServiceListingService(ServiceListingRepository repository,
                                 CategoryService categoryService,
                                 WorkerProfileService workerProfileService) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.workerProfileService = workerProfileService;
    }

    public boolean hasProfile(String workerId) {
        return workerProfileService.getOwnProfile(workerId).isPresent();
    }

    public List<ServiceListingDto> listForWorker(String workerId) {
        Map<String, CategoryDto> categories = categoryMap();
        return repository.findByWorkerIdOrderByCreatedAtDesc(workerId).stream()
                .map(s -> ServiceListingDto.from(s, categories.get(s.getCategoryId())))
                .toList();
    }

    /** A single service of this worker as a DTO (photos included). Unknown or foreign ids give a 404. */
    public ServiceListingDto getOwned(String workerId, String serviceId) {
        return toDto(findOwned(workerId, serviceId));
    }

    public ServiceListingForm getFormForEdit(String workerId, String serviceId) {
        ServiceListing s = findOwned(workerId, serviceId);
        ServiceListingForm form = new ServiceListingForm();
        form.setCategoryId(s.getCategoryId());
        form.setTitle(s.getTitle());
        form.setDescription(s.getDescription());
        form.setIncludesText(s.getIncludes() == null ? "" : String.join("\n", s.getIncludes()));
        form.setPrice(s.getPrice());
        form.setPriceType(s.getPriceType());
        form.setDurationMinutes(s.getDurationMinutes());
        form.setServiceArea(s.getServiceArea());
        form.setServiceRadiusKm(s.getServiceRadiusKm());
        if (s.getGeoLocation() != null) {
            form.setLongitude(s.getGeoLocation().getX());
            form.setLatitude(s.getGeoLocation().getY());
        }
        return form;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public ServiceListingDto create(String workerId, ServiceListingForm form) {
        if (!hasProfile(workerId)) {
            throw new ValidationException("Create your professional profile before adding services.");
        }
        if (repository.countByWorkerId(workerId) >= MAX_SERVICES_PER_WORKER) {
            throw new ValidationException("You can list up to " + MAX_SERVICES_PER_WORKER + " services.");
        }
        validateCategory(form.getCategoryId());

        ServiceListing listing = ServiceListing.builder()
                .workerId(workerId)
                .slug(uniqueSlug(form.getTitle()))
                .status(ServiceStatus.ACTIVE)
                .workerApproved(isWorkerApproved(workerId))
                .build();
        apply(listing, form);
        return toDto(save(listing));
    }

    /** The slug is kept on edit so public links stay valid. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public ServiceListingDto update(String workerId, String serviceId, ServiceListingForm form) {
        ServiceListing listing = findOwned(workerId, serviceId);
        validateCategory(form.getCategoryId());
        apply(listing, form);
        listing.setWorkerApproved(isWorkerApproved(workerId));
        return toDto(save(listing));
    }

    /** Workers may switch between ACTIVE and INACTIVE; SUSPENDED is controlled by admins only. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public void setActive(String workerId, String serviceId, boolean active) {
        ServiceListing listing = findOwned(workerId, serviceId);
        if (listing.getStatus() == ServiceStatus.SUSPENDED) {
            throw new AccessForbiddenException("This service was suspended by an administrator.");
        }
        listing.setStatus(active ? ServiceStatus.ACTIVE : ServiceStatus.INACTIVE);
        save(listing);
    }

    // ---------- Helpers ----------

    private boolean isWorkerApproved(String workerId) {
        return workerProfileService.getOwnProfile(workerId).map(WorkerProfileDto::isVerified).orElse(false);
    }

    private ServiceListing findOwned(String workerId, String serviceId) {
        return repository.findByIdAndWorkerId(serviceId, workerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
    }

    private void validateCategory(String categoryId) {
        boolean valid = categoryService.getActiveCategories().stream()
                .anyMatch(c -> c.getId().equals(categoryId));
        if (!valid) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of("categoryId", "Please choose a valid category."));
        }
    }

    private void apply(ServiceListing listing, ServiceListingForm form) {
        listing.setCategoryId(form.getCategoryId());
        listing.setTitle(form.getTitle().trim());
        listing.setDescription(form.getDescription().trim());
        listing.setIncludes(parseIncludes(form.getIncludesText()));
        listing.setPrice(form.getPrice().setScale(2, RoundingMode.HALF_UP));
        listing.setPriceType(form.getPriceType());
        listing.setDurationMinutes(form.getDurationMinutes());
        listing.setServiceArea(form.getServiceArea().trim());
        listing.setServiceRadiusKm(form.getServiceRadiusKm());
        listing.setGeoLocation(GeoUtil.toPoint(form.getLatitude(), form.getLongitude()));
    }

    private List<String> parseIncludes(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> items = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (items.size() > MAX_INCLUDES) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of("includesText", "Please list at most " + MAX_INCLUDES + " items."));
        }
        if (items.stream().anyMatch(s -> s.length() > MAX_INCLUDE_LENGTH)) {
            throw new ValidationException("Please fix the highlighted fields.",
                    Map.of("includesText", "Each item can be at most " + MAX_INCLUDE_LENGTH + " characters."));
        }
        return new ArrayList<>(items);
    }

    private ServiceListing save(ServiceListing listing) {
        try {
            return repository.save(listing);
        } catch (DuplicateKeyException ex) {
            throw new ValidationException("Could not save the service. Please try again.");
        }
    }

    private ServiceListingDto toDto(ServiceListing listing) {
        return ServiceListingDto.from(listing, categoryMap().get(listing.getCategoryId()));
    }

    private Map<String, CategoryDto> categoryMap() {
        return categoryService.getAllCategories().stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));
    }

    private String uniqueSlug(String title) {
        String base = SlugUtil.toSlug(title);
        if (base.isEmpty()) {
            base = "service";
        }
        String candidate = base;
        int suffix = 2;
        while (repository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
