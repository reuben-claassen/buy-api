package com.buyapi.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.buyapi.dto.request.ProductRequest;
import com.buyapi.dto.response.Responses.ProductResponse;
import com.buyapi.entity.Product;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CategoryRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.repository.UserRepository;
import com.buyapi.service.impl.ProductService;
import com.buyapi.service.impl.SupabaseStorageService;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock UserRepository userRepository;
    @Mock SupabaseStorageService storageService;

    @InjectMocks ProductService productService;

    private User seller(Long id, String email) {
        return User.builder().id(id).email(email).fullName("Seller")
                .role(User.Role.SELLER).build();
    }

    private Product product(Long id, User seller) {
        return Product.builder().id(id).name("Widget").description("desc")
                .price(new BigDecimal("9.99")).stock(10).active(true)
                .seller(seller).build();
    }

    private ProductRequest validRequest() {
        return new ProductRequest("Widget", "desc", new BigDecimal("9.99"), 10, null);
    }

    @Test
    void create_asSeller_setsSellerOnProduct() {
        User seller = seller(1L, "seller@example.com");
        when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(validRequest(), "seller@example.com");

        assertThat(response.sellerId()).isEqualTo(1L);
        verify(userRepository).findByEmail("seller@example.com");
    }

    @Test
    void create_asAdmin_sellerIsNull() {
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(validRequest(), null);

        assertThat(response.sellerId()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void create_unknownSellerEmail_throwsNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(validRequest(), "ghost@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_asAdmin_canUpdateAnyProduct() {
        Product p = product(1L, seller(2L, "other@example.com"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> productService.update(1L, validRequest(), "admin@example.com", true));
    }

    @Test
    void update_asSeller_ownsProduct_succeeds() {
        User seller = seller(1L, "seller@example.com");
        Product p = product(1L, seller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> productService.update(1L, validRequest(), "seller@example.com", false));
    }

    @Test
    void update_asSeller_doesNotOwnProduct_throwsAccessDenied() {
        User otherSeller = seller(2L, "other@example.com");
        Product p = product(1L, otherSeller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.update(1L, validRequest(), "seller@example.com", false))
                .isInstanceOf(AccessDeniedException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void update_asSeller_productHasNoSeller_throwsAccessDenied() {
        Product p = product(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.update(1L, validRequest(), "seller@example.com", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_productNotFound_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, validRequest(), "admin@example.com", true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadImage_asAdmin_uploadsToSupabaseAndSetsUrl() throws IOException {
        Product p = product(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(storageService.upload(any(), any())).thenReturn("https://supabase.example.com/storage/v1/object/public/products/product-1-uuid.jpg");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ProductResponse response = productService.uploadImage(1L, file, "admin@example.com", true);

        assertThat(response.imageUrl()).startsWith("https://supabase.example.com");
        verify(storageService).upload(any(), eq(file));
        verify(productRepository).save(p);
    }

    @Test
    void uploadImage_asSeller_ownsProduct_succeeds() throws IOException {
        User seller = seller(1L, "seller@example.com");
        Product p = product(1L, seller);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(storageService.upload(any(), any())).thenReturn("https://supabase.example.com/storage/v1/object/public/products/product-1-uuid.jpg");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatNoException().isThrownBy(
                () -> productService.uploadImage(1L, file, "seller@example.com", false));

        verify(storageService).upload(any(), eq(file));
    }

    @Test
    void uploadImage_nonImageFile_throwsBadRequest() {
        Product p = product(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> productService.uploadImage(1L, file, "admin@example.com", true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("image");

        verifyNoInteractions(storageService);
        verify(productRepository, never()).save(any());
    }

    @Test
    void uploadImage_storageFailure_propagatesException() throws IOException {
        Product p = product(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(storageService.upload(any(), any())).thenThrow(new IOException("Network error"));

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> productService.uploadImage(1L, file, "admin@example.com", true))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Network error");

        verify(productRepository, never()).save(any());
    }

    @Test
    void uploadImage_pathContainsProductIdAndExtension() throws IOException {
        Product p = product(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(storageService.upload(any(), any())).thenReturn("https://example.com/img.png");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", new byte[]{1});
        productService.uploadImage(1L, file, "admin@example.com", true);

        verify(storageService).upload(
                argThat(path -> path.startsWith("product-1-") && path.endsWith(".png")),
                eq(file)
        );
    }

    @Test
    void uploadImage_productNotFound_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> productService.uploadImage(99L, file, "admin@example.com", true))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void delete_existingProduct_softDeletes() {
        Product p = product(1L, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        productService.delete(1L);

        assertThat(p.isActive()).isFalse();
        verify(productRepository).save(p);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}