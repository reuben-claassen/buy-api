package com.buyapi.service.impl;

import com.buyapi.dto.request.CategoryRequest;
import com.buyapi.dto.response.Responses.CategoryResponse;
import com.buyapi.entity.Category;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllRootCategories() {
        return categoryRepository.findRootCategories()
                .stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return CategoryResponse.from(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new BadRequestException("Category already exists: " + request.name());
        }
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .slug(toSlug(request.name()))
                .build();

        if (request.parentId() != null) {
            category.setParent(findOrThrow(request.parentId()));
        }
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        category.setName(request.name());
        category.setDescription(request.description());
        category.setSlug(toSlug(request.name()));
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) throw new BadRequestException("Category cannot be its own parent");
            category.setParent(findOrThrow(request.parentId()));
        } else {
            category.setParent(null);
        }
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        if (!category.getChildren().isEmpty()) {
            throw new BadRequestException("Cannot delete category with sub-categories");
        }
        categoryRepository.delete(category);
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private String toSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
