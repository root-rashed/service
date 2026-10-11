package com.example.marketplace.admin.controller;

import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.category.dto.CategoryDto;
import com.example.marketplace.category.dto.CategoryForm;
import com.example.marketplace.category.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Category administration. Reuses {@link CategoryService} directly: its create/update/toggle
 * methods already evict the category, service-detail and popular-service caches, so this
 * controller adds routing and flash messages and nothing else.
 */
@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * One page handles both create and edit: when {@code id} is supplied the form is pre-filled
     * and posts back to {@code /admin/categories/{id}}.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String id, Model model) {
        List<CategoryDto> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("activeSection", "categories");

        if (id == null || id.isBlank()) {
            model.addAttribute("form", new CategoryForm());
            model.addAttribute("editingId", null);
            return "admin/categories";
        }

        CategoryDto editing = categories.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Category"));
        CategoryForm form = new CategoryForm();
        form.setName(editing.getName());
        form.setDescription(editing.getDescription());
        form.setIcon(editing.getIcon());
        form.setTone(editing.getTone());
        form.setDisplayOrder(editing.getDisplayOrder());
        model.addAttribute("form", form);
        model.addAttribute("editingId", editing.getId());
        return "admin/categories";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CategoryForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            // Re-render with what was typed so the administrator does not retype the category.
            return reRender(model, null);
        }
        CategoryDto created = categoryService.create(form);
        flash.addFlashAttribute("successMessage", "Category \"" + created.getName() + "\" created.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute("form") CategoryForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            return reRender(model, id);
        }
        CategoryDto updated = categoryService.update(id, form);
        flash.addFlashAttribute("successMessage", "Category \"" + updated.getName() + "\" updated.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/active")
    public String activate(@PathVariable String id, RedirectAttributes flash) {
        categoryService.setActive(id, true);
        flash.addFlashAttribute("successMessage", "Category is visible in the marketplace again.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/inactive")
    public String deactivate(@PathVariable String id, RedirectAttributes flash) {
        categoryService.setActive(id, false);
        flash.addFlashAttribute("successMessage",
                "Category hidden from the marketplace. Existing listings keep their link.");
        return "redirect:/admin/categories";
    }

    /**
     * After a failed validation the {@code form} model attribute already holds what was typed,
     * so only the surrounding page data needs reloading.
     */
    private String reRender(Model model, String editingId) {
        List<CategoryDto> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("editingId", editingId);
        model.addAttribute("activeSection", "categories");
        return "admin/categories";
    }
}
