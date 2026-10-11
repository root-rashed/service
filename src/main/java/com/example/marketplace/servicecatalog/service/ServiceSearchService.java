package com.example.marketplace.servicecatalog.service;

import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.service.CategoryService;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.util.GeoUtil;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.dto.ServiceSearchCriteria;
import com.example.marketplace.servicecatalog.dto.ServiceSort;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Public service search: keyword, category, location text, radius, price range, minimum rating,
 * sorting and pagination. Only ACTIVE services of APPROVED workers in ACTIVE categories are returned.
 * Not cached on purpose: results depend on the filters. Real-time availability is never cached.
 */
@Service
public class ServiceSearchService {

    private static final int PAGE_SIZE = 12;
    private static final int MAX_TERMS = 5;
    private static final int DEFAULT_RADIUS_KM = 10;

    private enum GeoMode { NONE, WITHIN, NEAR }

    private final MongoTemplate mongoTemplate;
    private final CategoryService categoryService;

    public ServiceSearchService(MongoTemplate mongoTemplate, CategoryService categoryService) {
        this.mongoTemplate = mongoTemplate;
        this.categoryService = categoryService;
    }

    public Page<ServiceListingDto> search(ServiceSearchCriteria c) {
        int page = Math.max(c.getPage() == null ? 0 : c.getPage(), 0);
        PageRequest pageable = PageRequest.of(page, PAGE_SIZE);

        List<CategoryDto> categories = categoryService.getActiveCategories();
        Map<String, CategoryDto> byId = categories.stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));

        Optional<CategoryDto> selected = Optional.empty();
        if (c.getCategory() != null && !c.getCategory().isBlank()) {
            selected = categories.stream().filter(cat -> cat.getSlug().equals(c.getCategory().trim())).findFirst();
            if (selected.isEmpty()) {
                return Page.empty(pageable); // unknown or inactive category
            }
        }

        boolean geo = c.hasGeo();
        ServiceSort sort = resolveSort(c, geo);
        int radiusKm = clamp(c.getRadius() == null ? DEFAULT_RADIUS_KM : c.getRadius(), 1, 100);
        boolean nearestFirst = geo && sort == ServiceSort.DISTANCE;

        // $near/$nearSphere cannot be used by count queries, so the total uses $geoWithin.
        long total = mongoTemplate.count(
                new Query(buildCriteria(c, categories, selected, geo ? GeoMode.WITHIN : GeoMode.NONE, radiusKm)),
                ServiceListing.class);

        Query query = new Query(buildCriteria(c, categories, selected,
                !geo ? GeoMode.NONE : (nearestFirst ? GeoMode.NEAR : GeoMode.WITHIN), radiusKm));
        if (!nearestFirst) {
            query.with(sortFor(sort));
        }
        query.skip((long) page * PAGE_SIZE).limit(PAGE_SIZE);

        List<ServiceListingDto> content = mongoTemplate.find(query, ServiceListing.class).stream()
                .map(s -> toDto(s, byId.get(s.getCategoryId()), geo ? c : null))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    // ---------- Query building ----------

    private Criteria buildCriteria(ServiceSearchCriteria c, List<CategoryDto> categories,
                                   Optional<CategoryDto> selected, GeoMode geoMode, int radiusKm) {
        Criteria criteria = Criteria.where("status").is(ServiceStatus.ACTIVE)
                .and("workerApproved").is(true);

        if (selected.isPresent()) {
            criteria.and("categoryId").is(selected.get().getId());
        } else {
            criteria.and("categoryId").in(categories.stream().map(CategoryDto::getId).toList());
        }

        BigDecimal min = nonNegative(c.getMinPrice());
        BigDecimal max = nonNegative(c.getMaxPrice());
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal tmp = min;
            min = max;
            max = tmp;
        }
        if (min != null && max != null) {
            criteria.and("price").gte(min).lte(max);
        } else if (min != null) {
            criteria.and("price").gte(min);
        } else if (max != null) {
            criteria.and("price").lte(max);
        }

        if (c.getMinRating() != null && c.getMinRating() > 0) {
            criteria.and("ratingAverage").gte(Math.min(c.getMinRating(), 5.0));
        }

        String location = trimToNull(c.getLocation());
        if (location != null) {
            criteria.and("serviceArea").regex(Pattern.quote(limit(location, 60)), "i");
        }

        List<Criteria> terms = keywordCriteria(c.getQ(), categories);
        if (!terms.isEmpty()) {
            criteria.andOperator(terms.toArray(new Criteria[0]));
        }

        Point point = geoMode == GeoMode.NONE ? null : new Point(c.getLng(), c.getLat());
        if (geoMode == GeoMode.WITHIN) {
            criteria.and("geoLocation").withinSphere(new Circle(point, new Distance(radiusKm, Metrics.KILOMETERS)));
        } else if (geoMode == GeoMode.NEAR) {
            criteria.and("geoLocation").nearSphere(point)
                    .maxDistance(new Distance(radiusKm, Metrics.KILOMETERS).getNormalizedValue());
        }
        return criteria;
    }

    /** Every keyword must match the title, description, area, an included item or the category name. */
    private List<Criteria> keywordCriteria(String q, List<CategoryDto> categories) {
        List<Criteria> result = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return result;
        }
        Arrays.stream(q.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .map(t -> limit(t, 40))
                .filter(t -> t.length() >= 2)
                .distinct()
                .limit(MAX_TERMS)
                .forEach(term -> {
                    String stem = stem(term);
                    String regex = Pattern.quote(stem);
                    List<Criteria> alternatives = new ArrayList<>(List.of(
                            Criteria.where("title").regex(regex, "i"),
                            Criteria.where("description").regex(regex, "i"),
                            Criteria.where("serviceArea").regex(regex, "i"),
                            Criteria.where("includes").regex(regex, "i")));
                    List<String> categoryIds = categories.stream()
                            .filter(cat -> cat.getName().toLowerCase(Locale.ROOT).contains(stem))
                            .map(CategoryDto::getId)
                            .toList();
                    if (!categoryIds.isEmpty()) {
                        alternatives.add(Criteria.where("categoryId").in(categoryIds));
                    }
                    result.add(new Criteria().orOperator(alternatives.toArray(new Criteria[0])));
                });
        return result;
    }

    /** Very light stemming so "plumber" also finds "plumbing" and "cleaner" finds "cleaning". */
    private String stem(String term) {
        if (term.length() > 5 && term.endsWith("ing")) {
            return term.substring(0, term.length() - 3);
        }
        if (term.length() > 4 && term.endsWith("er")) {
            return term.substring(0, term.length() - 2);
        }
        if (term.length() > 3 && term.endsWith("s")) {
            return term.substring(0, term.length() - 1);
        }
        return term;
    }

    private ServiceSort resolveSort(ServiceSearchCriteria c, boolean geo) {
        ServiceSort sort = c.getSort() == null ? ServiceSort.RELEVANCE : c.getSort();
        return (sort == ServiceSort.DISTANCE && !geo) ? ServiceSort.RELEVANCE : sort;
    }

    private Sort sortFor(ServiceSort sort) {
        return switch (sort) {
            case PRICE_LOW -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"));
            case PRICE_HIGH -> Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"));
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
            case RELEVANCE, RATING, DISTANCE -> Sort.by(
                    Sort.Order.desc("ratingAverage"), Sort.Order.desc("ratingCount"),
                    Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        };
    }

    // ---------- Mapping and small helpers ----------

    private ServiceListingDto toDto(ServiceListing s, CategoryDto category, ServiceSearchCriteria geoOrNull) {
        ServiceListingDto dto = ServiceListingDto.from(s, category);
        if (geoOrNull != null && s.getGeoLocation() != null) {
            double km = GeoUtil.distanceKm(geoOrNull.getLat(), geoOrNull.getLng(),
                    s.getGeoLocation().getY(), s.getGeoLocation().getX());
            return dto.withLocation(dto.getLocation() + " \u00b7 " + String.format(Locale.ROOT, "%.1f km", km));
        }
        return dto;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return (value == null || value.signum() < 0) ? null : value;
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
