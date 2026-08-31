package com.ecommerce.service;

import com.ecommerce.entity.Category;
import com.ecommerce.exception.AuthException;
import com.ecommerce.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> all() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Category create(String name, String description) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new AuthException("Category name is required.");
        if (categoryRepository.existsByName(trimmed)) throw new AuthException("Category already exists.");
        Category c = new Category();
        c.setName(trimmed);
        c.setDescription(description);
        c.setStatus("ACTIVE");
        return categoryRepository.save(c);
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AuthException("Category not found: " + id));
    }
}
