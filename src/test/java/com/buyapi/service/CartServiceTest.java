package com.buyapi.service;

import com.buyapi.dto.request.CartItemRequest;
import com.buyapi.dto.response.Responses.CartResponse;
import com.buyapi.entity.*;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CartItemRepository;
import com.buyapi.repository.CartRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.repository.UserRepository;
import com.buyapi.service.impl.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;

    @InjectMocks CartService cartService;

    private User user;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("user@example.com")
                .fullName("Test User").role(User.Role.CUSTOMER).build();
        cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        product = Product.builder().id(5L).name("Widget")
                .price(new BigDecimal("9.99")).stock(50).active(true).build();
    }

    private void stubUserAndCart() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    }

    @Test
    void getCart_emptyCart_returnsZeroTotal() {
        stubUserAndCart();

        CartResponse response = cartService.getCart("user@example.com");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCart_unknownEmail_throwsNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCart("ghost@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCart_missingCart_throwsNotFound() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCart("user@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItem_newProduct_addsItemToCart() {
        stubUserAndCart();
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(10L, 5L)).thenReturn(Optional.empty());

        CartItem saved = CartItem.builder().id(1L).cart(cart).product(product).quantity(3).build();
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(saved);

        cart.getItems().add(saved);

        CartResponse response = cartService.addItem("user@example.com", new CartItemRequest(5L, 3));

        assertThat(response.items()).hasSize(1);
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addItem_existingProduct_incrementsQuantity() {
        stubUserAndCart();
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        CartItem existing = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(existing);
        when(cartItemRepository.findByCartIdAndProductId(10L, 5L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any())).thenReturn(existing);

        cartService.addItem("user@example.com", new CartItemRequest(5L, 3));

        assertThat(existing.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).save(existing);
    }

    @Test
    void addItem_insufficientStock_throwsBadRequest() {
        stubUserAndCart();
        product.setStock(2);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem("user@example.com", new CartItemRequest(5L, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void addItem_unknownProduct_throwsNotFound() {
        stubUserAndCart();
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem("user@example.com", new CartItemRequest(99L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_validQuantity_updatesItem() {
        stubUserAndCart();
        CartItem item = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(any())).thenReturn(item);

        cartService.updateItem("user@example.com", 1L, new CartItemRequest(5L, 5));

        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    void updateItem_quantityZero_deletesItem() {
        stubUserAndCart();
        CartItem item = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(item);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        cartService.updateItem("user@example.com", 1L, new CartItemRequest(5L, 0));

        verify(cartItemRepository).delete(item);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void updateItem_itemBelongsToDifferentCart_throwsBadRequest() {
        stubUserAndCart();
        Cart otherCart = Cart.builder().id(99L).user(user).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder().id(1L).cart(otherCart).product(product).quantity(2).build();
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItem("user@example.com", 1L, new CartItemRequest(5L, 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to your cart");
    }

    @Test
    void updateItem_insufficientStock_throwsBadRequest() {
        stubUserAndCart();
        product.setStock(2);
        CartItem item = CartItem.builder().id(1L).cart(cart).product(product).quantity(1).build();
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItem("user@example.com", 1L, new CartItemRequest(5L, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void updateItem_itemNotFound_throwsNotFound() {
        stubUserAndCart();
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem("user@example.com", 999L, new CartItemRequest(5L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItem_ownedItem_removesFromCart() {
        stubUserAndCart();
        CartItem item = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(item);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        cartService.removeItem("user@example.com", 1L);

        verify(cartItemRepository).delete(item);
        assertThat(cart.getItems()).doesNotContain(item);
    }

    @Test
    void removeItem_itemBelongsToDifferentCart_throwsBadRequest() {
        stubUserAndCart();
        Cart otherCart = Cart.builder().id(99L).user(user).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder().id(1L).cart(otherCart).product(product).quantity(2).build();
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.removeItem("user@example.com", 1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void removeItem_itemNotFound_throwsNotFound() {
        stubUserAndCart();
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem("user@example.com", 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearCart_withItems_emptiesCartAndSaves() {
        stubUserAndCart();
        CartItem item = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(item);

        cartService.clearCart("user@example.com");

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }

    @Test
    void clearCart_alreadyEmpty_savesWithNoChange() {
        stubUserAndCart();

        cartService.clearCart("user@example.com");

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }
}
