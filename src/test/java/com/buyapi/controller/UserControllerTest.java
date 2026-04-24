package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.dto.response.Responses.UserResponse;
import com.buyapi.service.impl.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

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
    void update_admin_returnsUpdatedUser() throws Exception {
        UserResponse updated = new UserResponse(1L, "new@example.com", "New Name", "SELLER", Instant.now());
        given(userService.updateUser(eq(1L), any())).willReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"email\":\"new@example.com\",\"role\":\"SELLER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("SELLER"));

        verify(userService).updateUser(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_missingRequiredField_returns400() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_invalidEmail_returns400() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"email\":\"not-an-email\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void update_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"email\":\"jane@example.com\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void update_seller_returns403() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"email\":\"jane@example.com\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"email\":\"jane@example.com\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isUnauthorized());
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
}