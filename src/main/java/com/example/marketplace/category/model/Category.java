package com.example.marketplace.category.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A service category such as "Home Services". Services reference it by id. */
@Document(collection = "categories")
@CompoundIndex(name = "active_order", def = "{'active': 1, 'displayOrder': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;

    /** Bootstrap Icons class, e.g. bi-house-door. */
    private String icon;

    /** Colour tone key used by the UI: orange, pink, blue, purple, green, indigo, rose, sky. */
    private String tone;

    private int displayOrder;

    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
