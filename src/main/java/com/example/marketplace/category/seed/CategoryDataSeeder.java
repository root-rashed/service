package com.example.marketplace.category.seed;

import com.example.marketplace.category.dto.CategoryForm;
import com.example.marketplace.category.repository.CategoryRepository;
import com.example.marketplace.category.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the starter categories on first run (only when the collection is empty). */
@Slf4j
@Component
public class CategoryDataSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    public CategoryDataSeeder(CategoryRepository categoryRepository, CategoryService categoryService) {
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }
        seed("Home Services", "Cleaning, repair, maintenance", "bi-house-door", "orange", 1);
        seed("Beauty & Wellness", "Salon, massage, fitness", "bi-scissors", "pink", 2);
        seed("IT & Tech", "Support, development, setup", "bi-laptop", "blue", 3);
        seed("Tutoring & Education", "Online and home tutors", "bi-mortarboard", "purple", 4);
        seed("Health & Fitness", "Trainers, nutrition, therapy", "bi-heart-pulse", "green", 5);
        seed("Business Services", "Marketing, design, accounting", "bi-briefcase", "indigo", 6);
        seed("Events & Photography", "Photography, event planning", "bi-camera", "rose", 7);
        log.info("Seeded {} starter categories", categoryRepository.count());
    }

    private void seed(String name, String description, String icon, String tone, int order) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setDescription(description);
        form.setIcon(icon);
        form.setTone(tone);
        form.setDisplayOrder(order);
        categoryService.create(form);
    }
}
