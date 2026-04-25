package com.buyapi.controller;

import com.buyapi.config.WebMvcTestSecurityConfig;
import com.buyapi.dto.request.CartItemRequest;
import com.buyapi.dto.response.Responses.CartItemResponse;
import com.buyapi.dto.response.Responses.CartResponse;
import com.buyapi.service.impl.CartService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for cart endpoints.
 *
 * Notes:
 * - Uses @WithMockUser because endpoints rely on @AuthenticationPrincipal.
 * - Unauthenticated cases are not covered here (handled in integration tests).
 * - ObjectMapper is instantiated manually (not auto-configured in this slice).
 */
@WebMvcTest(controllers = CartController.class)
@Import(WebMvcTestSecurityConfig.class)
class CartControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CartService cartService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private CartResponse emptyCart() {
        return new CartResponse(1L, List.of(), BigDecimal.ZERO);
    }

    private CartResponse cartWithItem() {
        CartItemResponse item = new CartItemResponse(
                10L, 5L, "Widget", "http://example.com/widget.jpg", new BigDecimal("9.99"), 2, new BigDecimal("19.98"), 20);
        return new CartResponse(1L, List.of(item), new BigDecimal("19.98"));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void getCart_authenticated_returnsOk() throws Exception {
        given(cartService.getCart("jane@example.com")).willReturn(emptyCart());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void addItem_validRequest_returnsUpdatedCart() throws Exception {
        CartItemRequest request = new CartItemRequest(5L, 2);
        given(cartService.addItem(eq("jane@example.com"), any(CartItemRequest.class)))
                .willReturn(cartWithItem());

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Widget"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(19.98));
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void addItem_nullProductId_returns400() throws Exception {
        CartItemRequest request = new CartItemRequest(null, 2);

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void addItem_zeroQuantity_returns400() throws Exception {
        CartItemRequest request = new CartItemRequest(5L, 0);

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void updateItem_validRequest_returnsOk() throws Exception {
        CartItemRequest request = new CartItemRequest(5L, 3);
        given(cartService.updateItem(eq("jane@example.com"), eq(10L), any(CartItemRequest.class)))
                .willReturn(cartWithItem());

        mockMvc.perform(put("/api/cart/items/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(cartService).updateItem(eq("jane@example.com"), eq(10L), any());
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void removeItem_existingItem_returnsOk() throws Exception {
        given(cartService.removeItem("jane@example.com", 10L)).willReturn(emptyCart());

        mockMvc.perform(delete("/api/cart/items/10"))
                .andExpect(status().isOk());

        verify(cartService).removeItem("jane@example.com", 10L);
    }

    @Test
    @WithMockUser(username = "jane@example.com")
    void clearCart_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isNoContent());

        verify(cartService).clearCart("jane@example.com");
    }
}