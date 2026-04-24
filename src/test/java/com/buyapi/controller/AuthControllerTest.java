package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.request.AuthRequest;
import com.buyapi.dto.response.Responses.AuthResponse;
import com.buyapi.service.impl.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for authentication endpoints.
 *
 * Notes:
 * - Uses @MockitoBean (Spring Boot 4 replacement for @MockBean).
 * - ObjectMapper is instantiated manually as it is not auto-configured in this slice.
 */
@WebMvcTest(controllers = AuthController.class)
@Import(WebMvcTestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    AuthService authService;

    @Test
    void register_validRequest_returns201WithToken() throws Exception {
        AuthRequest.Register request = new AuthRequest.Register(
                "jane@example.com", "securePass1", "Jane Doe", null);

        AuthResponse response = new AuthResponse("jwt-token", 1L, "jane@example.com", "Jane Doe", "CUSTOMER");
        given(authService.register(any(AuthRequest.Register.class))).willReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_withRoleField_returns201() throws Exception {
        AuthRequest.Register request = new AuthRequest.Register(
                "jane@example.com", "securePass1", "Jane Doe", "SELLER");

        AuthResponse response = new AuthResponse("jwt-token", 1L, "jane@example.com", "Jane Doe", "SELLER");
        given(authService.register(any(AuthRequest.Register.class))).willReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void register_blankEmail_returns400() throws Exception {
        AuthRequest.Register request = new AuthRequest.Register("", "securePass1", "Jane Doe", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        AuthRequest.Register request = new AuthRequest.Register("not-an-email", "securePass1", "Jane Doe", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        AuthRequest.Register request = new AuthRequest.Register("jane@example.com", "short", "Jane Doe", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_blankFullName_returns400() throws Exception {
        AuthRequest.Register request = new AuthRequest.Register("jane@example.com", "securePass1", "", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        AuthRequest.Login request = new AuthRequest.Login("jane@example.com", "securePass1");

        AuthResponse response = new AuthResponse("jwt-token", 1L, "jane@example.com", "Jane Doe", "CUSTOMER");
        given(authService.login(any(AuthRequest.Login.class))).willReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        AuthRequest.Login request = new AuthRequest.Login("jane@example.com", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        AuthRequest.Login request = new AuthRequest.Login("", "securePass1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authEndpoints_arePublic_noTokenRequired() throws Exception {
        given(authService.login(any())).willReturn(
                new AuthResponse("t", 1L, "e@e.com", "E", "CUSTOMER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"e@e.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());
    }
}