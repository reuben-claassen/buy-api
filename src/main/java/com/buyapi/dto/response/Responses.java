package com.buyapi.dto.response;

import com.buyapi.entity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class Responses {

    public record AuthResponse(String token, String email, String fullName, String role) {}

    public record UserResponse(Long id, String email, String fullName, String role, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(),
                    u.getRole().name(), u.getCreatedAt());
        }
    }

    public record CategoryResponse(Long id, String name, String description, Long parentId,
                                   List<CategoryResponse> children) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(
                    c.getId(), c.getName(), c.getDescription(),
                    c.getParent() != null ? c.getParent().getId() : null,
                    c.getChildren() == null ? List.of() :
                            c.getChildren().stream().map(CategoryResponse::fromShallow).toList()
            );
        }
        public static CategoryResponse fromShallow(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(),
                    c.getParent() != null ? c.getParent().getId() : null, List.of());
        }
    }

    public record ProductResponse(Long id, String name, String description, BigDecimal price,
                                  Integer stock, String imageUrl, boolean active,
                                  CategoryResponse category, Instant createdAt) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(
                    p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getStock(), p.getImageUrl(), p.isActive(),
                    p.getCategory() != null ? CategoryResponse.fromShallow(p.getCategory()) : null,
                    p.getCreatedAt()
            );
        }
    }

    public record CartResponse(Long id, List<CartItemResponse> items, BigDecimal total) {
        public static CartResponse from(Cart c) {
            List<CartItemResponse> items = c.getItems().stream()
                    .map(CartItemResponse::from).toList();
            BigDecimal total = items.stream()
                    .map(CartItemResponse::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CartResponse(c.getId(), items, total);
        }
    }

    public record CartItemResponse(Long id, Long productId, String productName,
                                   BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {
        public static CartItemResponse from(CartItem ci) {
            return new CartItemResponse(
                    ci.getId(), ci.getProduct().getId(), ci.getProduct().getName(),
                    ci.getProduct().getPrice(), ci.getQuantity(), ci.getSubtotal()
            );
        }
    }

    public record OrderResponse(Long id, String status, BigDecimal totalAmount,
                                String shippingAddress, List<OrderItemResponse> items,
                                Instant createdAt) {
        public static OrderResponse from(Order o) {
            return new OrderResponse(
                    o.getId(), o.getStatus().name(), o.getTotalAmount(),
                    o.getShippingAddress(),
                    o.getItems().stream().map(OrderItemResponse::from).toList(),
                    o.getCreatedAt()
            );
        }
    }

    public record OrderItemResponse(Long id, Long productId, String productName,
                                    Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        public static OrderItemResponse from(OrderItem oi) {
            return new OrderItemResponse(
                    oi.getId(), oi.getProduct().getId(), oi.getProduct().getName(),
                    oi.getQuantity(), oi.getUnitPrice(), oi.getSubtotal()
            );
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
