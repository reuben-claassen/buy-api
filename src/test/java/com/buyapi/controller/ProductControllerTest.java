package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.request.ProductRequest;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.dto.response.Responses.ProductResponse;
import com.buyapi.service.impl.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for product endpoints.
 *
 * Notes:
 * - Access control is handled entirely by SecurityConfig URL-level rules 
 *   and service-level ownership checks.
 *   Role-based blocking (e.g. CUSTOMER/unauthenticated blocked from write endpoints)
 *   is covered by SecurityIntegrationTest.
 * - create/update/uploadImage/removeImage inject @AuthenticationPrincipal, so the
 *   service receives the caller's email and an isAdmin/isSeller flag derived from
 *   their role.
 * - ObjectMapper is instantiated manually (not auto-configured in this slice).
 */
@WebMvcTest(controllers = ProductController.class)
@Import(WebMvcTestSecurityConfig.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService productService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private ProductResponse sampleProduct() {
        return new ProductResponse(
                1L, "Widget", "A fine widget", new BigDecimal("9.99"),
                100, null, true, null, null, Instant.now());
    }

    private ProductResponse sampleProductWithSeller(Long sellerId) {
        return new ProductResponse(
                1L, "Widget", "A fine widget", new BigDecimal("9.99"),
                100, null, true, null, sellerId, Instant.now());
    }

    private PageResponse<ProductResponse> singlePage(ProductResponse p) {
        return new PageResponse<>(List.of(p), 0, 20, 1, 1);
    }

    @Test
    void search_noParams_returnsOkWithPage() throws Exception {
        given(productService.search(isNull(), isNull(), any())).willReturn(singlePage(sampleProduct()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Widget"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void search_withQueryAndCategory_passesParamsToService() throws Exception {
        given(productService.search(eq("wid"), eq(5L), any())).willReturn(singlePage(sampleProduct()));

        mockMvc.perform(get("/api/products").param("q", "wid").param("categoryId", "5"))
                .andExpect(status().isOk());

        verify(productService).search(eq("wid"), eq(5L), any());
    }

    @Test
    void search_isPublic_noAuthRequired() throws Exception {
        given(productService.search(any(), any(), any())).willReturn(singlePage(sampleProduct()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void getById_existingProduct_returnsOk() throws Exception {
        given(productService.getById(1L)).willReturn(sampleProduct());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void getById_isPublic_noAuthRequired() throws Exception {
        given(productService.getById(anyLong())).willReturn(sampleProduct());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void create_asAdmin_passesNullSellerEmail() throws Exception {
        ProductRequest request = new ProductRequest("Widget", "A fine widget", new BigDecimal("9.99"), 100, null);
        given(productService.create(any(ProductRequest.class), isNull())).willReturn(sampleProduct());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Widget"));

        verify(productService).create(any(ProductRequest.class), isNull());
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void create_asSeller_passesSellerEmail() throws Exception {
        ProductRequest request = new ProductRequest("Widget", "A fine widget", new BigDecimal("9.99"), 100, null);
        given(productService.create(any(ProductRequest.class), eq("seller@example.com")))
                .willReturn(sampleProductWithSeller(2L));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(2));

        verify(productService).create(any(ProductRequest.class), eq("seller@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_blankName_returns400() throws Exception {
        ProductRequest request = new ProductRequest("", "desc", new BigDecimal("9.99"), 10, null);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_priceBelowMinimum_returns400() throws Exception {
        ProductRequest request = new ProductRequest("Widget", "desc", new BigDecimal("0.00"), 10, null);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_negativeStock_returns400() throws Exception {
        ProductRequest request = new ProductRequest("Widget", "desc", new BigDecimal("9.99"), -1, null);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void update_asAdmin_passesIsAdminTrue() throws Exception {
        ProductRequest request = new ProductRequest("Widget v2", "Updated", new BigDecimal("12.99"), 50, null);
        given(productService.update(eq(1L), any(ProductRequest.class), eq("admin@example.com"), eq(true)))
                .willReturn(sampleProduct());

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(productService).update(eq(1L), any(ProductRequest.class), eq("admin@example.com"), eq(true));
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void update_asSeller_passesIsAdminFalse() throws Exception {
        ProductRequest request = new ProductRequest("Widget v2", "Updated", new BigDecimal("12.99"), 50, null);
        given(productService.update(eq(1L), any(ProductRequest.class), eq("seller@example.com"), eq(false)))
                .willReturn(sampleProduct());

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(productService).update(eq(1L), any(ProductRequest.class), eq("seller@example.com"), eq(false));
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void update_sellerDoesNotOwnProduct_returns403() throws Exception {
        ProductRequest request = new ProductRequest("Widget v2", "Updated", new BigDecimal("12.99"), 50, null);
        given(productService.update(eq(1L), any(ProductRequest.class), eq("seller@example.com"), eq(false)))
                .willThrow(new org.springframework.security.access.AccessDeniedException("You do not own this product"));

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void uploadImage_asAdmin_returnsOkWithUpdatedProduct() throws Exception {
        ProductResponse responseWithImage = new ProductResponse(
                1L, "Widget", "A fine widget", new BigDecimal("9.99"),
                100, "http://example.com/widget.jpg", true, null, null, Instant.now());

        given(productService.uploadImage(eq(1L), any(), eq("admin@example.com"), eq(true)))
                .willReturn(responseWithImage);

        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "widget.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/products/1/image").file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("http://example.com/widget.jpg"));
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void uploadImage_asSeller_ownProduct_returnsOk() throws Exception {
        ProductResponse responseWithImage = new ProductResponse(
                1L, "Widget", "A fine widget", new BigDecimal("9.99"),
                100, "http://example.com/widget.jpg", true, null, 2L, Instant.now());

        given(productService.uploadImage(eq(1L), any(), eq("seller@example.com"), eq(false)))
                .willReturn(responseWithImage);

        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "widget.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/products/1/image").file(imageFile))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void uploadImage_sellerDoesNotOwnProduct_returns403() throws Exception {
        given(productService.uploadImage(eq(1L), any(), eq("seller@example.com"), eq(false)))
                .willThrow(new org.springframework.security.access.AccessDeniedException("You do not own this product"));

        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "widget.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/products/1/image").file(imageFile))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadImage_nonImageContentType_returns400() throws Exception {
        given(productService.uploadImage(eq(1L), any(), any(), anyBoolean()))
                .willThrow(new com.buyapi.exception.BadRequestException("File must be an image"));

        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "not an image".getBytes());

        mockMvc.perform(multipart("/api/products/1/image").file(textFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadImage_productNotFound_returns404() throws Exception {
        given(productService.uploadImage(eq(99L), any(), any(), anyBoolean()))
                .willThrow(new com.buyapi.exception.ResourceNotFoundException("Product", 99L));

        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "photo.png", MediaType.IMAGE_PNG_VALUE, "fake-png".getBytes());

        mockMvc.perform(multipart("/api/products/99/image").file(imageFile))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadImage_emptyFile_returns400() throws Exception {
        given(productService.uploadImage(eq(1L), any(), any(), anyBoolean()))
                .willThrow(new com.buyapi.exception.BadRequestException("File must not be empty"));

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/products/1/image").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_existingProduct_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }
}