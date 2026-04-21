package com.buyapi.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buyapi.dto.request.OrderRequest;
import com.buyapi.dto.response.Responses.OrderResponse;
import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.entity.Order;
import com.buyapi.entity.OrderItem;
import com.buyapi.entity.Product;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.OrderRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.repository.UserRepository;
import com.buyapi.service.EmailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(String email, Pageable pageable) {
        Long userId = findUser(email).getId();
        Page<Order> page = orderRepository.findByUserId(userId, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(OrderResponse::from).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id, String email, boolean isAdmin) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (!isAdmin && !order.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("Order does not belong to you");
        }
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse placeOrder(String email, OrderRequest request) {
        User user = findUser(email);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest ir : request.items()) {
            Product product = productRepository.findById(ir.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", ir.productId()));

            if (!product.isActive()) {
                throw new BadRequestException("Product is not available: " + product.getName());
            }
            if (product.getStock() < ir.quantity()) {
                throw new BadRequestException("Insufficient stock for: " + product.getName()
                        + " (available: " + product.getStock() + ")");
            }

            product.setStock(product.getStock() - ir.quantity());
            productRepository.save(product);

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(ir.quantity())
                    .unitPrice(product.getPrice())
                    .build();
            items.add(item);
            total = total.add(item.getSubtotal());
        }

        Order order = Order.builder()
                .user(user)
                .shippingAddress(request.shippingAddress())
                .totalAmount(total)
                .build();

        items.forEach(item -> item.setOrder(order));
        order.setItems(items);

        Order saved = orderRepository.save(order);

        try { cartService.clearCart(email); } catch (Exception ignored) {}

        emailService.sendOrderConfirmation(saved);

        return OrderResponse.from(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(Long id, String callerEmail, boolean isAdmin, boolean isSeller) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (isSeller) {
            assertSellerOwnsAllItems(order, callerEmail);
        } else if (!isAdmin) {
            if (!order.getUser().getEmail().equals(callerEmail)) {
                throw new AccessDeniedException("Order does not belong to you");
            }
        }

        if (order.getStatus() == Order.Status.SHIPPED || order.getStatus() == Order.Status.DELIVERED) {
            throw new BadRequestException("Cannot cancel an order that has already been shipped");
        }

        order.getItems().forEach(item -> {
            Product p = item.getProduct();
            p.setStock(p.getStock() + item.getQuantity());
            productRepository.save(p);
        });

        order.setStatus(Order.Status.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, String status, String callerEmail, boolean isAdmin,
                                      boolean isSeller) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!isAdmin && isSeller) {
            assertSellerOwnsAllItems(order, callerEmail);
        }

        try {
            order.setStatus(Order.Status.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(Pageable pageable) {
        Page<Order> page = orderRepository.findAll(pageable);
        return new PageResponse<>(
                page.getContent().stream().map(OrderResponse::from).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()
        );
    }

    private void assertSellerOwnsAllItems(Order order, String sellerEmail) {
        boolean allOwned = order.getItems().stream().allMatch(item -> {
            User seller = item.getProduct().getSeller();
            return seller != null && seller.getEmail().equals(sellerEmail);
        });
        if (!allOwned) {
            throw new AccessDeniedException(
                    "You can only manage orders that contain exclusively your own products");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
