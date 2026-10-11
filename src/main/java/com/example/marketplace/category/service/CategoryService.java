package com.example.marketplace.category.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.dto.CategoryForm;
import com.example.marketplace.category.model.Category;
import com.example.marketplace.category.repository.CategoryRepository;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.common.util.SlugUtil;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Category reads are cached with Spring Cache (see CacheNames.CATEGORIES);
 * every write evicts the category cache and the service caches that embed category data.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // ---------- Public reads (cached) ----------

    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "'active'")
    public List<CategoryDto> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "'slug:' + #root.args[0]")
    public CategoryDto getActiveBySlug(String slug) {
        return categoryRepository.findBySlugAndActiveTrue(slug)
                .map(CategoryDto::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Category"));
    }

    // ---------- Admin reads (not cached) ----------

    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    // ---------- Admin writes (evict cache) ----------

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public CategoryDto create(CategoryForm form) {
        Category category = Category.builder()
                .name(form.getName().trim())
                .slug(uniqueSlug(form.getName()))
                .description(blankToNull(form.getDescription()))
                .icon(form.getIcon())
                .tone(form.getTone())
                .displayOrder(form.getDisplayOrder())
                .active(true)
                .build();
        return CategoryDto.from(save(category));
    }

    /** Slug stays stable on rename so existing links and search-engine URLs keep working. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public CategoryDto update(String id, CategoryForm form) {
        Category category = findById(id);
        category.setName(form.getName().trim());
        category.setDescription(blankToNull(form.getDescription()));
        category.setIcon(form.getIcon());
        category.setTone(form.getTone());
        category.setDisplayOrder(form.getDisplayOrder());
        return CategoryDto.from(save(category));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public void setActive(String id, boolean active) {
        Category category = findById(id);
        category.setActive(active);
        save(category);
    }

    // ---------- Helpers ----------

    private Category findById(String id) {
        return categoryRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category"));
    }

    private Category save(Category category) {
        try {
            return categoryRepository.save(category);
        } catch (DuplicateKeyException ex) {
            throw new ValidationException("Category could not be saved.",
                    Map.of("name", "A category with a similar name already exists."));
        }
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.toSlug(name);
        if (base.isEmpty()) {
            base = "category";
        }
        String candidate = base;
        int suffix = 2;
        while (categoryRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
