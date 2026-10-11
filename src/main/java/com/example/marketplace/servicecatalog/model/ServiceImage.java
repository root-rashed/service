package com.example.marketplace.servicecatalog.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Image metadata embedded in a ServiceListing (the file itself lives in storage).
 * The list order is the display order; the first image is the primary one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceImage {

    /** Stable id used in management URLs. */
    private String id;

    /** Storage key, e.g. "service-images/3f2a...c1.jpg". */
    private String key;

    private Instant createdAt;
}
