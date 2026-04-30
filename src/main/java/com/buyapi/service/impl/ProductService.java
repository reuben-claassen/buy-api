package com.buyapi.service.impl;

import java.io.IOException;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.buyapi.dto.request.ProductRequest;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.dto.response.Responses.ProductResponse;
import com.buyapi.entity.Category;
import com.buyapi.entity.Product;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CategoryRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService storageService;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String query, Long categoryId, Pageable pageable) {
        Page<Product> page;
        if (query == null || query.isBlank()) {
            page = productRepository.searchWithoutQuery(categoryId, pageable);
        } else {
            page = productRepository.searchWithQuery(query, categoryId, pageable);
        }
        return toPageResponse(page);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request, String sellerEmail) {
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stock(request.stock())
                .build();
        if (request.categoryId() != null) {
            product.setCategory(findCategory(request.categoryId()));
        }
        if (sellerEmail != null) {
            User seller = userRepository.findByEmail(sellerEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + sellerEmail));
            product.setSeller(seller);
        }
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, String callerEmail, boolean isAdmin) {
        Product product = isAdmin ? findOrThrow(id) : findOwnedOrThrow(id, callerEmail);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        if (request.categoryId() != null) {
            product.setCategory(findCategory(request.categoryId()));
        } else {
            product.setCategory(null);
        }
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse uploadImage(Long id, MultipartFile file, String callerEmail, boolean isAdmin)
            throws IOException {
        Product product = isAdmin ? findOrThrow(id) : findOwnedOrThrow(id, callerEmail);

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("File must be an image");
        }

        String ext = getExtension(file.getOriginalFilename());
        String path = "product-" + id + "-" + UUID.randomUUID() + ext;
        String publicUrl = storageService.upload(path, file);

        product.setImageUrl(publicUrl);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse removeImage(Long id, String callerEmail, boolean isAdmin) {
        Product product = isAdmin ? findOrThrow(id) : findOwnedOrThrow(id, callerEmail);
        product.setImageUrl(null);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        Product product = findOrThrow(id);
        product.setActive(false);
        productRepository.save(product);
    }

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Product findOwnedOrThrow(Long id, String sellerEmail) {
        Product product = findOrThrow(id);
        if (product.getSeller() == null || !product.getSeller().getEmail().equals(sellerEmail)) {
            throw new AccessDeniedException("You do not own this product");
        }
        return product;
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private PageResponse<ProductResponse> toPageResponse(Page<Product> page) {
        return new PageResponse<>(
                page.getContent().stream().map(ProductResponse::from).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()
        );
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}