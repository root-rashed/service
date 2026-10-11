package com.example.marketplace.category.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Admin input for creating or editing a category. */
@Getter
@Setter
public class CategoryForm {

    @NotBlank(message = "Please enter a category name.")
    @Size(min = 2, max = 60, message = "Name must be between 2 and 60 characters.")
    private String name;

    @Size(max = 160, message = "Description can be at most 160 characters.")
    private String description;

    @NotBlank(message = "Please enter an icon class, for example bi-house-door.")
    @Pattern(regexp = "^bi-[a-z0-9-]{1,40}$", message = "Icon must be a Bootstrap Icons class such as bi-house-door.")
    private String icon = "bi-grid";

    @NotBlank(message = "Please choose a colour tone.")
    @Pattern(regexp = "^(orange|pink|blue|purple|green|indigo|rose|sky)$", message = "Please choose a valid colour tone.")
    private String tone = "orange";

    @NotNull(message = "Please enter a display order.")
    @Min(value = 0, message = "Display order cannot be negative.")
    @Max(value = 1000, message = "Display order cannot exceed 1000.")
    private Integer displayOrder = 0;
}
