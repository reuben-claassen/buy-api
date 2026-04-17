package com.buyapi.service;

import com.buyapi.dto.request.CategoryRequest;
import com.buyapi.dto.response.Responses.CategoryResponse;
import com.buyapi.entity.Category;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CategoryRepository;
import com.buyapi.service.impl.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;

    @InjectMocks CategoryService categoryService;

    private Category sampleCategory(Long id, String name) {
        return Category.builder().id(id).name(name).description("desc")
                .slug(name.toLowerCase()).children(new ArrayList<>()).build();
    }

    @Test
    void getAll_returnsAllCategories() {
        Category c1 = sampleCategory(1L, "Electronics");
        Category c2 = sampleCategory(2L, "Books");
        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoryResponse> result = categoryService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryResponse::name)
                .containsExactly("Electronics", "Books");
    }

    @Test
    void getAllRootCategories_returnsOnlyRoots() {
        Category root = sampleCategory(1L, "Electronics");
        when(categoryRepository.findRootCategories()).thenReturn(List.of(root));

        List<CategoryResponse> result = categoryService.getAllRootCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Electronics");
        assertThat(result.get(0).parentId()).isNull();
    }

    @Test
    void getById_existingCategory_returnsResponse() {
        Category category = sampleCategory(1L, "Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Electronics");
    }

    @Test
    void getById_missingCategory_throwsNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_uniqueName_savesAndReturns() {
        when(categoryRepository.existsByName("Electronics")).thenReturn(false);
        Category saved = sampleCategory(1L, "Electronics");
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = categoryService.create(
                new CategoryRequest("Electronics", "Electronic goods", null));

        assertThat(response.name()).isEqualTo("Electronics");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_generatesSlugFromName() {
        when(categoryRepository.existsByName("Home & Garden")).thenReturn(false);
        Category saved = sampleCategory(1L, "Home & Garden");
        saved.setSlug("home-garden");
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            assertThat(c.getSlug()).isEqualTo("home-garden");
            return c;
        });

        categoryService.create(new CategoryRequest("Home & Garden", "Home stuff", null));

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_duplicateName_throwsBadRequest() {
        when(categoryRepository.existsByName("Electronics")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(
                new CategoryRequest("Electronics", "desc", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Category already exists");
    }

    @Test
    void create_withParent_setsParentOnCategory() {
        when(categoryRepository.existsByName("Laptops")).thenReturn(false);
        Category parent = sampleCategory(1L, "Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        Category saved = sampleCategory(2L, "Laptops");
        saved.setParent(parent);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = categoryService.create(
                new CategoryRequest("Laptops", "Laptop computers", 1L));

        assertThat(response.parentId()).isEqualTo(1L);
    }

    @Test
    void create_withMissingParent_throwsNotFound() {
        when(categoryRepository.existsByName("Laptops")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(
                new CategoryRequest("Laptops", "desc", 999L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_validRequest_updatesFields() {
        Category existing = sampleCategory(1L, "Old Name");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.update(1L,
                new CategoryRequest("New Name", "New desc", null));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("New desc");
        assertThat(response.parentId()).isNull();
    }

    @Test
    void update_categoryAsItsOwnParent_throwsBadRequest() {
        Category existing = sampleCategory(1L, "Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.update(1L,
                new CategoryRequest("Electronics", "desc", 1L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be its own parent");
    }

    @Test
    void update_withNewParent_setsParent() {
        Category existing = sampleCategory(1L, "Laptops");
        Category parent = sampleCategory(2L, "Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.update(1L,
                new CategoryRequest("Laptops", "desc", 2L));

        assertThat(response.parentId()).isEqualTo(2L);
    }

    @Test
    void update_notFound_throwsNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L,
                new CategoryRequest("X", "desc", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_leafCategory_deletesSuccessfully() {
        Category leaf = sampleCategory(1L, "Laptops");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(leaf));

        categoryService.delete(1L);

        verify(categoryRepository).delete(leaf);
    }

    @Test
    void delete_categoryWithChildren_throwsBadRequest() {
        Category parent = sampleCategory(1L, "Electronics");
        Category child = sampleCategory(2L, "Laptops");
        parent.getChildren().add(child);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sub-categories");
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
