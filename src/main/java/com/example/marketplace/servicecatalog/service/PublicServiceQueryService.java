package com.example.marketplace.servicecatalog.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.servicecatalog.dto.ServiceDetailView;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.repository.ServiceListingRepository;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import com.example.marketplace.worker.service.WorkerProfileService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Cached public reads: single service detail and the homepage "popular services" list.
 * Only ACTIVE services of APPROVED workers in ACTIVE categories are ever returned.
 * Caches are cleared by ServiceListingService (service edits) and ServiceVisibilitySync
 * (worker approval changes). Booking, availability and payment state are never cached here.
 */
@Service
public class PublicServiceQueryService {

    private final ServiceListingRepository repository;
    private final CategoryService categoryService;
    private final WorkerProfileService workerProfileService;

    public PublicServiceQueryService(ServiceListingRepository repository,
                                     CategoryService categoryService,
                                     WorkerProfileService workerProfileService) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.workerProfileService = workerProfileService;
    }

    @Cacheable(cacheNames = CacheNames.SERVICE_DETAILS, key = "'slug:' + #root.args[0]")
    public ServiceDetailView getDetail(String slug) {
        var listing = repository.findBySlugAndStatusAndWorkerApprovedTrue(slug, ServiceStatus.ACTIVE)
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
        CategoryDto category = categoryService.getActiveCategories().stream()
                .filter(c -> c.getId().equals(listing.getCategoryId()))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
        // Authoritative live check, in case the denormalized workerApproved flag is stale.
        WorkerProfileDto worker = workerProfileService.getPublicByUserId(listing.getWorkerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
        return new ServiceDetailView(ServiceListingDto.from(listing, category), worker, category.getSlug());
    }

    @Cacheable(cacheNames = CacheNames.POPULAR_SERVICES, key = "'top:' + #root.args[0]")
    public List<ServiceListingDto> getPopular(int limit) {
        List<CategoryDto> categories = categoryService.getActiveCategories();
        if (categories.isEmpty()) {
            return List.of();
        }
        Map<String, CategoryDto> byId = categories.stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));
        Sort sort = Sort.by(
                Sort.Order.desc("ratingAverage"),
                Sort.Order.desc("ratingCount"),
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("id"));
        return repository.findByStatusAndWorkerApprovedTrueAndCategoryIdIn(
                        ServiceStatus.ACTIVE,
                        byId.keySet(),
                        PageRequest.of(0, Math.max(1, Math.min(limit, 12)), sort))
                .stream()
                .map(s -> ServiceListingDto.from(s, byId.get(s.getCategoryId())))
                .toList();
    }
}
