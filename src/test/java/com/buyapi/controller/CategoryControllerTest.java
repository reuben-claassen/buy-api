package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.request.CategoryRequest;
import com.buyapi.dto.response.Responses.CategoryResponse;
import com.buyapi.service.impl.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for category endpoints.
 *
 * Notes:
 * - URL-level role rules from SecurityConfig are NOT loaded in this slice — those
 *   are covered by SecurityIntegrationTest (e.g. CUSTOMER/unauthenticated blocked,
 *   SELLER blocked from delete).
 * - @WithMockUser is used to satisfy authenticated controller execution and to verify
 *   that SELLER is permitted to create/update via the SecurityConfig URL rules
 *   (confirmed in SecurityIntegrationTest) and service delegation here.
 * - ObjectMapper is instantiated manually (not auto-configured in this slice).
 */
@WebMvcTest(controllers = CategoryController.class)
@Import(WebMvcTestSecurityConfig.class)
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CategoryService categoryService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private CategoryResponse electronics() {
        return new CategoryResponse(1L, "Electronics", "All things electronic", null, List.of());
    }

    private CategoryResponse phones() {
        return new CategoryResponse(2L, "Phones", "Mobile devices", 1L, List.of());
    }

    @Test
    void getRoots_returnsOkWithList() throws Exception {
        given(categoryService.getAllRootCategories()).willReturn(List.of(electronics()));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[0].parentId").isEmpty());
    }

    @Test
    void getRoots_isPublic_noAuthRequired() throws Exception {
        given(categoryService.getAllRootCategories()).willReturn(List.of());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_returnsAllCategories() throws Exception {
        given(categoryService.getAll()).willReturn(List.of(electronics(), phones()));

        mockMvc.perform(get("/api/categories/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].parentId").value(1));
    }

    @Test
    void getById_existingCategory_returnsOk() throws Exception {
        given(categoryService.getById(1L)).willReturn(electronics());

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void getById_isPublic_noAuthRequired() throws Exception {
        given(categoryService.getById(anyLong())).willReturn(electronics());

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201() throws Exception {
        CategoryRequest request = new CategoryRequest("Electronics", "All things electronic", null);
        given(categoryService.create(any(CategoryRequest.class))).willReturn(electronics());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void create_asSeller_returns201() throws Exception {
        CategoryRequest request = new CategoryRequest("Electronics", "All things electronic", null);
        given(categoryService.create(any(CategoryRequest.class))).willReturn(electronics());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withParent_setsParentId() throws Exception {
        CategoryRequest request = new CategoryRequest("Phones", "Mobile devices", 1L);
        given(categoryService.create(any(CategoryRequest.class))).willReturn(phones());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_blankName_returns400() throws Exception {
        CategoryRequest request = new CategoryRequest("", "desc", null);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returnsOk() throws Exception {
        CategoryRequest request = new CategoryRequest("Electronics v2", "Updated", null);
        given(categoryService.update(eq(1L), any(CategoryRequest.class))).willReturn(electronics());

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void update_asSeller_returnsOk() throws Exception {
        CategoryRequest request = new CategoryRequest("Electronics v2", "Updated", null);
        given(categoryService.update(eq(1L), any(CategoryRequest.class))).willReturn(electronics());

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }
}