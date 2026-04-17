package com.buyapi.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.web.multipart.MultipartFile;

import com.buyapi.dto.request.ProductRequest;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.dto.response.Responses.ProductResponse;
import com.buyapi.entity.Category;
import com.buyapi.entity.Product;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CategoryRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.service.impl.ProductService;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;

    @TempDir Path tempDir;

    @InjectMocks ProductService productService;

    private Product sampleProduct() {
        return Product.builder()
                .id(1L)
                .name("Widget")
                .description("A fine widget")
                .price(new BigDecimal("9.99"))
                .stock(100)
                .active(true)
                .build();
    }

    private ProductService serviceWithTempDir() {
        return new ProductService(productRepository, categoryRepository, tempDir);
    }

    @Test
    void getById_existingProduct_returnsResponse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct()));

        ProductResponse response = productService.getById(1L);

        assertThat(response.name()).isEqualTo("Widget");
        assertThat(response.price()).isEqualByComparingTo("9.99");
    }

    @Test
    void getById_missingProduct_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_validRequest_savesAndReturns() {
        ProductRequest request = new ProductRequest("Widget", "Desc",
                new BigDecimal("9.99"), 100, null);

        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(request);

        assertThat(response.name()).isEqualTo("Widget");
        verify(productRepository).save(any());
    }

    @Test
    void create_withCategory_setsCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest("Widget", "Desc",
                new BigDecimal("9.99"), 10, 1L);

        productService.create(request);

        verify(categoryRepository).findById(1L);
    }

    @Test
    void create_withInvalidCategory_throws() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        ProductRequest request = new ProductRequest("Widget", "Desc",
                new BigDecimal("9.99"), 10, 99L);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_existingProduct_updatesFields() {
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);

        ProductRequest request = new ProductRequest("New", "New Desc",
                new BigDecimal("19.99"), 50, null);

        productService.update(1L, request);

        assertThat(product.getName()).isEqualTo("New");
        assertThat(product.getCategory()).isNull();
    }

    @Test
    void update_withCategory_setsCategory() {
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category()));
        when(productRepository.save(any())).thenReturn(product);

        ProductRequest request = new ProductRequest("Name", "Desc",
                new BigDecimal("10"), 5, 1L);

        productService.update(1L, request);

        assertThat(product.getCategory()).isNotNull();
    }

    @Test
    void update_productNotFound_throws() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(1L, mock(ProductRequest.class)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_invalidCategory_throws() {
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        ProductRequest request = new ProductRequest("Name", "Desc",
                new BigDecimal("10"), 5, 99L);

        assertThatThrownBy(() -> productService.update(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadImage_invalidContentType_throws() throws Exception {
        MultipartFile file = mock(MultipartFile.class);

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct()));
        when(file.getContentType()).thenReturn("text/plain");

        assertThatThrownBy(() -> productService.uploadImage(1L, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadImage_nullContentType_throws() throws Exception {
        MultipartFile file = mock(MultipartFile.class);

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct()));
        when(file.getContentType()).thenReturn(null);

        assertThatThrownBy(() -> productService.uploadImage(1L, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadImage_validImage_savesFileAndSetsUrl() throws Exception {
        ProductService service = serviceWithTempDir();
        MultipartFile file = mock(MultipartFile.class);
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("image.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(productRepository.save(any())).thenReturn(product);

        service.uploadImage(1L, file);

        assertThat(product.getImageUrl()).contains("/uploads/");
        assertThat(Files.list(tempDir)).isNotEmpty();
    }

    @Test
    void uploadImage_nullFilename_defaultsToJpg() throws Exception {
        ProductService service = serviceWithTempDir();

        MultipartFile file = mock(MultipartFile.class);
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(productRepository.save(any())).thenReturn(product);

        service.uploadImage(1L, file);

        assertThat(product.getImageUrl()).endsWith(".jpg");
    }

    @Test
    void uploadImage_noExtension_defaultsToJpg() throws Exception {
        ProductService service = serviceWithTempDir();
        MultipartFile file = mock(MultipartFile.class);
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("file");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(productRepository.save(any())).thenReturn(product);

        service.uploadImage(1L, file);

        assertThat(product.getImageUrl()).endsWith(".jpg");
    }

    @Test
    void search_returnsPagedResults() {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), PageRequest.of(0, 20), 1);

        when(productRepository.search(any(), any(), any())).thenReturn(page);

        PageResponse<ProductResponse> result =
                productService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void delete_existingProduct_softDeletes() {
        Product product = sampleProduct();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);

        productService.delete(1L);

        assertThat(product.isActive()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void delete_productNotFound_throws() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}