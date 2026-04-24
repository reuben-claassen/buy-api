package com.buyapi.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buyapi.dto.request.CartItemRequest;
import com.buyapi.dto.response.Responses.CartResponse;
import com.buyapi.entity.Cart;
import com.buyapi.entity.CartItem;
import com.buyapi.entity.Product;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.CartItemRepository;
import com.buyapi.repository.CartRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {
        return CartResponse.from(findCartByEmail(email));
    }

    @Transactional
    public CartResponse addItem(String email, CartItemRequest request) {
        Cart cart = findCartByEmail(email);
        Product product = findProduct(request.productId());

        if (product.getStock() < request.quantity()) {
            throw new BadRequestException("Insufficient stock for product: " + product.getName());
        }

        CartItem item = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), product.getId())
                .orElseGet(() -> CartItem.builder().cart(cart).product(product).quantity(0).build());

        item.setQuantity(item.getQuantity() + request.quantity());
        cartItemRepository.save(item);

        return CartResponse.from(findCartByEmail(email));
    }

    @Transactional
    public CartResponse updateItem(String email, Long itemId, CartItemRequest request) {
        Cart cart = findCartByEmail(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("Item does not belong to your cart");
        }

        if (request.quantity() == 0) {
            cartItemRepository.delete(item);
        } else {
            if (request.quantity() > item.getQuantity() && item.getProduct().getStock() < request.quantity()) {
                throw new BadRequestException("Insufficient stock");
            }
            item.setQuantity(request.quantity());            cartItemRepository.save(item);
        }

        return CartResponse.from(findCartByEmail(email));
    }

    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        Cart cart = findCartByEmail(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("Item does not belong to your cart");
        }

        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        return CartResponse.from(findCartByEmail(email));
    }

    @Transactional
    public void clearCart(String email) {
        Cart cart = findCartByEmail(email);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public Cart findCartByEmail(String email) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email))
                .getId();
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + email));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
