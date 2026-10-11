package com.example.marketplace.category.dto;

import com.example.marketplace.category.model.Category;
import lombok.Value;

/** Read-only category view used by pages and cached by CategoryService. */
@Value
public class CategoryDto {

    String id;
    String name;
    String slug;
    String description;
    String icon;
    String tone;
    int displayOrder;
    boolean active;

    public static CategoryDto from(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getIcon(),
                category.getTone(),
                category.getDisplayOrder(),
                category.isActive());
    }
}
