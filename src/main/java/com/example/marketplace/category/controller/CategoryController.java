package com.example.marketplace.category.controller;

import com.example.marketplace.category.service.CategoryService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriUtils;

/** Public category pages. */
@Controller
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public String list(Model model) {
        model.addAttribute("categories", categoryService.getActiveCategories());
        model.addAttribute("activeNav", "services");
        return "categories/list";
    }

    /**
     * Short, stable, search-engine friendly URL for a category, e.g. /categories/plumbing.
     * The canonical page with real content is /services?category=plumbing, so this only
     * forwards there instead of rendering a duplicate page.
     */
    @GetMapping("/categories/{slug}")
    public String browse(@PathVariable String slug) {
        categoryService.getActiveBySlug(slug); // unknown/inactive category -> 404 page
        return "redirect:/services?category=" + UriUtils.encodeQueryParam(slug, StandardCharsets.UTF_8);
    }
}
