package com.buyapi.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.buyapi.dto.request.OrderRequest;
import com.buyapi.dto.response.Responses.OrderResponse;
import com.buyapi.entity.Order;
import com.buyapi.entity.OrderItem;
import com.buyapi.entity.Product;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.OrderRepository;
import com.buyapi.repository.ProductRepository;
import com.buyapi.repository.UserRepository;
import com.buyapi.service.impl.CartService;
import com.buyapi.service.impl.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    CartService cartService;
    @Mock
    EmailService emailService;

    @InjectMocks
    OrderService orderService;

    private User sampleUser() {
        return User.builder().id(1L).email("user@example.com")
                .fullName("Test User").role(User.Role.CUSTOMER).build();
    }

    private Product sampleProduct(int stock) {
        return Product.builder().id(1L).name("Widget")
                .price(new BigDecimal("10.00")).stock(stock).active(true).build();
    }

    @Test
    void getById_notFound_throws() {
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(1L, "user@example.com", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_notOwnerAndNotAdmin_throwsAccessDenied() {
        User owner = sampleUser();
        owner.setEmail("owner@example.com");

        Order order = Order.builder()
                .id(1L)
                .user(owner)
                .items(List.of())
                .build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getById(1L, "other@example.com", false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getById_admin_canAccessAnyOrder() {
        Order order = Order.builder()
                .id(1L)
                .user(sampleUser())
                .items(List.of())
                .build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getById(1L, "admin@example.com", true);

        assertThat(response).isNotNull();
    }

    @Test
    void getMyOrders_returnsPage() {
        User user = sampleUser();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(orderRepository.findByUserId(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        var result = orderService.getMyOrders("user@example.com",
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result).isNotNull();
    }

    @Test
    void getAllOrders_returnsCorrectPageData() {
        Page<Order> page = new PageImpl<>(
                List.of(Order.builder().id(1L).build()),
                PageRequest.of(0, 10),
                1
        );

        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(page);

        var result = orderService.getAllOrders(PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void placeOrder_sufficientStock_createsOrder() {
        User user = sampleUser();
        Product product = sampleProduct(50);
        OrderRequest request = new OrderRequest("123 Main St",
                List.of(new OrderRequest.OrderItemRequest(1L, 2)));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        doNothing().when(emailService).sendOrderConfirmation(any());

        OrderResponse response = orderService.placeOrder("user@example.com", request);

        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(product.getStock()).isEqualTo(48);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void placeOrder_clearsCart_afterSuccessfulOrder() {
        User user = sampleUser();
        Product product = sampleProduct(10);
        OrderRequest request = new OrderRequest("123 Main St",
                List.of(new OrderRequest.OrderItemRequest(1L, 1)));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        doNothing().when(emailService).sendOrderConfirmation(any());

        orderService.placeOrder("user@example.com", request);

        verify(cartService).clearCart("user@example.com");
    }

    @Test
    void placeOrder_insufficientStock_throwsBadRequest() {
        User user = sampleUser();
        Product product = sampleProduct(1);
        OrderRequest request = new OrderRequest("123 Main St",
                List.of(new OrderRequest.OrderItemRequest(1L, 5)));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.placeOrder("user@example.com", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void placeOrder_multipleItems_calculatesTotalCorrectly() {
        User user = sampleUser();

        Product p1 = sampleProduct(10);
        p1.setPrice(new BigDecimal("10"));

        Product p2 = sampleProduct(10);
        p2.setId(2L);
        p2.setPrice(new BigDecimal("5"));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendOrderConfirmation(any());

        OrderRequest request = new OrderRequest("addr", List.of(
                new OrderRequest.OrderItemRequest(1L, 2),
                new OrderRequest.OrderItemRequest(2L, 3)
        ));

        OrderResponse response = orderService.placeOrder("user@example.com", request);

        assertThat(response.totalAmount()).isEqualByComparingTo("35");
    }

    @Test
    void placeOrder_cartFails_stillSucceeds() {
        User user = sampleUser();
        Product product = sampleProduct(10);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendOrderConfirmation(any());

        doThrow(new RuntimeException("Cart failed"))
                .when(cartService).clearCart(any());

        OrderRequest request = new OrderRequest("addr",
                List.of(new OrderRequest.OrderItemRequest(1L, 1)));

        OrderResponse response = orderService.placeOrder("user@example.com", request);

        assertThat(response).isNotNull();
    }

    @Test
    void placeOrder_inactiveProduct_throwsBadRequest() {
        User user = sampleUser();
        Product product = sampleProduct(10);
        product.setActive(false);
        OrderRequest request = new OrderRequest("123 Main St",
                List.of(new OrderRequest.OrderItemRequest(1L, 1)));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.placeOrder("user@example.com", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void placeOrder_productNotFound_throws() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(sampleUser()));
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        OrderRequest request = new OrderRequest("addr",
                List.of(new OrderRequest.OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.placeOrder("user@example.com", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_valid_updatesStatus() {
        Order order = Order.builder()
                .id(1L)
                .status(Order.Status.PENDING)
                .items(List.of())
                .build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        OrderResponse response = orderService.updateStatus(1L, "shipped");

        assertThat(order.getStatus()).isEqualTo(Order.Status.SHIPPED);
    }

    @Test
    void updateStatus_invalid_throws() {
        Order order = Order.builder().id(1L).items(List.of()).build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(1L, "INVALID"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelOrder_pendingOrder_restoresStock() {
        User user = sampleUser();
        Product product = sampleProduct(10);
        OrderItem item = OrderItem.builder().product(product).quantity(3)
                .unitPrice(new BigDecimal("10.00")).build();
        Order order = Order.builder().id(1L).user(user)
                .status(Order.Status.PENDING).totalAmount(new BigDecimal("30.00"))
                .items(Arrays.asList(item)).build();
        item.setOrder(order);

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(productRepository.save(any())).thenReturn(product);
        when(orderRepository.save(any())).thenReturn(order);

        orderService.cancelOrder(1L, "user@example.com", false);

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(product.getStock()).isEqualTo(13);
    }

    @Test
    void cancelOrder_shippedOrder_throwsBadRequest() {
        User user = sampleUser();
        Order order = Order.builder().id(1L).user(user)
                .status(Order.Status.SHIPPED).totalAmount(BigDecimal.TEN)
                .items(List.of()).build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "user@example.com", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been shipped");
    }

    @Test
    void cancelOrder_admin_canCancelAnyOrder() {
        Order order = Order.builder()
                .id(1L)
                .user(sampleUser())
                .status(Order.Status.PENDING)
                .items(List.of())
                .build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.cancelOrder(1L, "admin@example.com", true);

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    void cancelOrder_delivered_throws() {
        Order order = Order.builder()
                .id(1L)
                .user(sampleUser())
                .status(Order.Status.DELIVERED)
                .items(List.of())
                .build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "user@example.com", false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelOrder_notOwner_throwsAccessDenied() {
        User owner = sampleUser();
        owner.setEmail("owner@example.com");

        Order order = Order.builder()
                .id(1L)
                .user(owner)
                .status(Order.Status.PENDING)
                .items(List.of())
                .build();

        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "other@example.com", false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
