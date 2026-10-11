package com.example.marketplace.servicecatalog.dto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.util.UriUtils;

/**
 * Public search / filter input, bound from query parameters of GET /services.
 * Everything is optional and is sanitized by ServiceSearchService; nothing here is trusted.
 */
@Getter
@Setter
public class ServiceSearchCriteria {

    /** Keywords. */
    private String q;

    /** Category slug. */
    private String category;

    /** Free-text area, matched against the service area. */
    private String location;

    /** Optional map position of the customer (from "use my location"). */
    private Double lat;
    private Double lng;

    /** Search radius in km (used only together with lat/lng). */
    private Integer radius;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    /** Minimum average rating, 0 to 5. */
    private Double minRating;

    private ServiceSort sort;

    /** Zero-based page index. */
    private Integer page;

    public boolean hasGeo() {
        return lat != null && lng != null
                && lat >= -90 && lat <= 90
                && lng >= -180 && lng <= 180;
    }

    /** All filters except the page number, URL-encoded, for building pagination links. */
    public String toQueryString() {
        StringJoiner joiner = new StringJoiner("&");
        add(joiner, "q", q);
        add(joiner, "category", category);
        add(joiner, "location", location);
        add(joiner, "lat", lat);
        add(joiner, "lng", lng);
        add(joiner, "radius", radius);
        add(joiner, "minPrice", minPrice);
        add(joiner, "maxPrice", maxPrice);
        add(joiner, "minRating", minRating);
        add(joiner, "sort", sort);
        return joiner.toString();
    }

    private void add(StringJoiner joiner, String name, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        joiner.add(name + "=" + UriUtils.encodeQueryParam(value.toString().trim(), StandardCharsets.UTF_8));
    }
}
