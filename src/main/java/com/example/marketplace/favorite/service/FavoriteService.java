package com.example.marketplace.favorite.service;

import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.favorite.model.Favorite;
import com.example.marketplace.favorite.repository.FavoriteRepository;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.servicecatalog.repository.ServiceListingRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Saved services. The user id always comes from the authenticated principal, so a customer can only
 * ever read or change their own favorites. Favorites are per-user data and are never cached.
 */
@Service
public class FavoriteService {

    private static final int PAGE_SIZE = 12;
    private static final int MAX_STATE_IDS = 60;

    private final FavoriteRepository favoriteRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final CategoryService categoryService;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           ServiceListingRepository serviceListingRepository,
                           CategoryService categoryService) {
        this.favoriteRepository = favoriteRepository;
        this.serviceListingRepository = serviceListingRepository;
        this.categoryService = categoryService;
    }

    /** Saves the service if it is not saved yet, otherwise removes it. Returns the new state. */
    public boolean toggle(String userId, String serviceId) {
        if (favoriteRepository.existsByUserIdAndServiceId(userId, serviceId)) {
            favoriteRepository.deleteByUserIdAndServiceId(userId, serviceId);
            return false;
        }
        // Only services that are publicly visible can be added.
        ServiceListing service = serviceListingRepository.findById(serviceId)
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE && s.isWorkerApproved())
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
        try {
            favoriteRepository.save(Favorite.builder().userId(userId).serviceId(service.getId()).build());
        } catch (DuplicateKeyException ex) {
            // A parallel request already saved it: the end state is the same.
        }
        return true;
    }

    /** The subset of the given service ids that this user has saved (at most 60 ids are checked). */
    public Set<String> favoriteIdsAmong(String userId, Collection<String> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Set.of();
        }
        List<String> limited = serviceIds.stream().distinct().limit(MAX_STATE_IDS).toList();
        return favoriteRepository.findByUserIdAndServiceIdIn(userId, limited).stream()
                .map(Favorite::getServiceId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * The user's saved services, newest first. Services that are no longer public (paused, suspended
     * or their worker not approved) are left out of the list but stay saved.
     */
    public Page<ServiceListingDto> list(String userId, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Favorite> favorites = favoriteRepository.findByUserId(userId, pageable);

        List<String> ids = favorites.getContent().stream().map(Favorite::getServiceId).toList();
        Map<String, ServiceListing> byId = serviceListingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ServiceListing::getId, Function.identity()));
        Map<String, CategoryDto> categories = categoryService.getAllCategories().stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));

        List<ServiceListingDto> content = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE && s.isWorkerApproved())
                .map(s -> ServiceListingDto.from(s, categories.get(s.getCategoryId())))
                .toList();
        return new PageImpl<>(content, pageable, favorites.getTotalElements());
    }

    /** The newest few saved services, for the customer dashboard. */
    public List<ServiceListingDto> recentSaved(String userId, int limit) {
        return list(userId, 0).getContent().stream().limit(Math.max(limit, 1)).toList();
    }
}
