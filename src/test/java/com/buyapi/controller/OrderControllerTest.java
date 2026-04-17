package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.request.OrderRequest;
import com.buyapi.dto.response.Responses.OrderResponse;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.service.impl.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * @WebMvcTest slice for order endpoints.
 *
 * Notes:
 * - Method-level security (@PreAuthorize) is enforced in this slice via
 *   @EnableMethodSecurity.
 * - Endpoints relying on @AuthenticationPrincipal require @WithMockUser.
 *   Unauthenticated flows are covered in integration tests.
 *
 * - ObjectMapper is instantiated manually (not auto-configured in this slice).
 */
@WebMvcTest(controllers = OrderController.class)
@Import(WebMvcTestSecurityConfig.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OrderService orderService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private OrderResponse sampleOrder(Long id, String status) {
        return new OrderResponse(id, status, new BigDecimal("19.99"),
                "123 Main St", List.of(), Instant.now());
    }

    private PageResponse<OrderResponse> singlePage(OrderResponse o) {
        return new PageResponse<>(List.of(o), 0, 10, 1, 1);
    }

    private OrderRequest validOrderRequest() {
        return new OrderRequest("123 Main St",
                List.of(new OrderRequest.OrderItemRequest(1L, 2)));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void getMyOrders_authenticated_returnsPage() throws Exception {
        given(orderService.getMyOrders(eq("jane@example.com"), any()))
                .willReturn(singlePage(sampleOrder(1L, "PENDING")));

        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void getMyOrders_customPagination_passesPageableToService() throws Exception {
        given(orderService.getMyOrders(any(), any()))
                .willReturn(singlePage(sampleOrder(1L, "PENDING")));

        mockMvc.perform(get("/api/orders/my").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(orderService).getMyOrders(eq("jane@example.com"), any());
    }

    @Test
    @WithMockUser(username = "jane@example.com", roles = "CUSTOMER")
    void getById_customer_passesIsAdminFalse() throws Exception {
        given(orderService.getById(1L, "jane@example.com", false))
                .willReturn(sampleOrder(1L, "PENDING"));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(orderService).getById(1L, "jane@example.com", false);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getById_admin_passesIsAdminTrue() throws Exception {
        given(orderService.getById(1L, "admin@example.com", true))
                .willReturn(sampleOrder(1L, "PENDING"));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk());

        verify(orderService).getById(1L, "admin@example.com", true);
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void placeOrder_validRequest_returns201() throws Exception {
        given(orderService.placeOrder(eq("jane@example.com"), any(OrderRequest.class)))
                .willReturn(sampleOrder(2L, "PENDING"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validOrderRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void placeOrder_blankShippingAddress_returns400() throws Exception {
        OrderRequest request = new OrderRequest("",
                List.of(new OrderRequest.OrderItemRequest(1L, 1)));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void placeOrder_emptyItemsList_returns400() throws Exception {
        OrderRequest request = new OrderRequest("123 Main St", List.of());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "jane@example.com", roles = "CUSTOMER")
    void cancel_customer_cancelsOwnOrder() throws Exception {
        given(orderService.cancelOrder(1L, "jane@example.com", false))
                .willReturn(sampleOrder(1L, "CANCELLED"));

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(orderService).cancelOrder(1L, "jane@example.com", false);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void cancel_admin_passesIsAdminTrue() throws Exception {
        given(orderService.cancelOrder(1L, "admin@example.com", true))
                .willReturn(sampleOrder(1L, "CANCELLED"));

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk());

        verify(orderService).cancelOrder(1L, "admin@example.com", true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_admin_returnsPage() throws Exception {
        given(orderService.getAllOrders(any()))
                .willReturn(singlePage(sampleOrder(1L, "PENDING")));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getAll_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_admin_returnsOk() throws Exception {
        given(orderService.updateStatus(1L, "SHIPPED")).willReturn(sampleOrder(1L, "SHIPPED"));

        mockMvc.perform(put("/api/orders/1/status").param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        verify(orderService).updateStatus(1L, "SHIPPED");
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateStatus_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/orders/1/status").param("status", "SHIPPED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/orders/1/status").param("status", "SHIPPED"))
                .andExpect(status().isUnauthorized());
    }
}