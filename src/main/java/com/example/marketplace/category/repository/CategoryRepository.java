package com.example.marketplace.category.repository;

import com.example.marketplace.category.model.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, String> {

    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

    List<Category> findAllByOrderByDisplayOrderAsc();

    Optional<Category> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);
}
