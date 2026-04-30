package com.buyapi.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.request.OrderRequest;
import com.buyapi.dto.request.OrderStatusRequest;
import com.buyapi.dto.response.Responses.OrderResponse;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.service.impl.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code @WebMvcTest} slice for order endpoints.
 *
 * The {@code getAll} and {@code updateStatus} endpoints are guarded by
 * {@code @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")}; method security is
 * activated via {@link WebMvcTestSecurityConfig}. Full security integration
 * (unauthenticated flows, URL-level rules) is covered by SecurityIntegrationTest.
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
        given(orderService.cancelOrder(1L, "jane@example.com", false, false))
                .willReturn(sampleOrder(1L, "CANCELLED"));

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(orderService).cancelOrder(1L, "jane@example.com", false, false);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void cancel_admin_passesIsAdminTrue() throws Exception {
        given(orderService.cancelOrder(1L, "admin@example.com", true, false))
                .willReturn(sampleOrder(1L, "CANCELLED"));

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk());

        verify(orderService).cancelOrder(1L, "admin@example.com", true, false);
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
        given(orderService.updateStatus(eq(1L), eq("SHIPPED"), any(), eq(true), eq(false))).willReturn(sampleOrder(1L, "SHIPPED"));

        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusRequest("SHIPPED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        verify(orderService).updateStatus(eq(1L), eq("SHIPPED"), any(), eq(true), eq(false));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateStatus_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusRequest("SHIPPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusRequest("SHIPPED"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void cancel_seller_passesIsSellerTrue() throws Exception {
        given(orderService.cancelOrder(1L, "seller@example.com", false, true))
                .willReturn(sampleOrder(1L, "CANCELLED"));

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk());

        verify(orderService).cancelOrder(1L, "seller@example.com", false, true);
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void updateStatus_seller_callsServiceWithIsSellerTrue() throws Exception {
        given(orderService.updateStatus(eq(1L), eq("SHIPPED"), eq("seller@example.com"), eq(false), eq(true)))
                .willReturn(sampleOrder(1L, "SHIPPED"));

        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusRequest("SHIPPED"))))
                .andExpect(status().isOk());

        verify(orderService).updateStatus(eq(1L), eq("SHIPPED"), eq("seller@example.com"), eq(false), eq(true));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getAll_asCustomer_returns403_via_preAuthorize() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateStatus_asCustomer_returns403_via_preAuthorize() throws Exception {
        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusRequest("SHIPPED"))))
                .andExpect(status().isForbidden());
    }

}