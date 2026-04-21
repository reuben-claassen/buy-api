package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.dto.response.Responses.UserResponse;
import com.buyapi.service.impl.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for user endpoints.
 *
 * Notes:
 * - Method-level security (@PreAuthorize) is enforced.
 * - @WithMockUser is required for endpoints using @AuthenticationPrincipal.
 * - Role-based and unauthenticated access is validated here where applicable,
 *   with integration tests covering full security behaviour.
 */
@WebMvcTest(controllers = UserController.class)
@Import(WebMvcTestSecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    private UserResponse sampleUser(Long id, String email) {
        return new UserResponse(id, email, "Jane Doe", "CUSTOMER", Instant.now());
    }

    private PageResponse<UserResponse> singlePage(UserResponse u) {
        return new PageResponse<>(List.of(u), 0, 20, 1, 1);
    }

    @Test
    @WithMockUser(username = "jane@example.com", roles = "CUSTOMER")
    void getMe_authenticated_returnsOwnProfile() throws Exception {
        given(userService.getMe("jane@example.com")).willReturn(sampleUser(1L, "jane@example.com"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_admin_returnsPageOfUsers() throws Exception {
        given(userService.getAll(any())).willReturn(singlePage(sampleUser(1L, "jane@example.com")));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("jane@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_customPageSize_passesParamsToService() throws Exception {
        given(userService.getAll(any())).willReturn(singlePage(sampleUser(1L, "a@b.com")));

        mockMvc.perform(get("/api/users").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        verify(userService).getAll(any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getAll_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_admin_returnsUser() throws Exception {
        given(userService.getById(1L)).willReturn(sampleUser(1L, "jane@example.com"));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getById_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void delete_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_admin_returnsUpdatedUser() throws Exception {
        given(userService.changeRole(1L, "SELLER"))
                .willReturn(new UserResponse(1L, "jane@example.com", "Jane Doe", "SELLER", java.time.Instant.now()));

        mockMvc.perform(patch("/api/users/1/role").param("role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"));

        verify(userService).changeRole(1L, "SELLER");
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void changeRole_seller_returns403() throws Exception {
        mockMvc.perform(patch("/api/users/1/role").param("role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changeRole_customer_returns403() throws Exception {
        mockMvc.perform(patch("/api/users/1/role").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/1/role").param("role", "SELLER"))
                .andExpect(status().isUnauthorized());
    }

}