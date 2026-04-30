package com.buyapi.security;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buyapi.service.impl.AuthService;
import com.buyapi.service.impl.CartService;
import com.buyapi.service.impl.CategoryService;
import com.buyapi.service.impl.OrderService;
import com.buyapi.service.impl.ProductService;
import com.buyapi.service.impl.SupabaseStorageService;
import com.buyapi.service.impl.UserService;

/**
 * Integration tests for URL-level security using the real SecurityFilterChain.
 *
 * Tests use {@code .with(user(...))} rather than {@code @WithMockUser} because
 * the JWT filter runs before the security context is populated by the annotation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean AuthService            authService;
    @MockitoBean ProductService         productService;
    @MockitoBean CategoryService        categoryService;
    @MockitoBean CartService            cartService;
    @MockitoBean OrderService           orderService;
    @MockitoBean UserService            userService;
    @MockitoBean SupabaseStorageService storageService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return user("user@example.com")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin() {
        return user("admin@example.com")
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asSeller() {
        return user("seller@example.com")
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    @Nested
    @DisplayName("Public endpoints — no authentication required")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /api/auth/login is publicly accessible")
        void authLogin_isPublic() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("POST /api/auth/register is publicly accessible")
        void authRegister_isPublic() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"new@example.com\",\"password\":\"password123\",\"fullName\":\"Test User\"}"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/products is publicly accessible")
        void getProducts_isPublic() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/products/{id} is publicly accessible")
        void getProductById_isPublic() throws Exception {
            mockMvc.perform(get("/api/products/1"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/categories is publicly accessible")
        void getCategories_isPublic() throws Exception {
            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/categories/{id} is publicly accessible")
        void getCategoryById_isPublic() throws Exception {
            mockMvc.perform(get("/api/categories/1"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/health is publicly accessible")
        void apiHealth_isPublic() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/health is publicly accessible")
        void actuatorHealth_isPublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /actuator/info is publicly accessible")
        void actuatorInfo_isPublic() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /swagger-ui/index.html is publicly accessible")
        void swaggerUi_isPublic() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("Protected endpoints — unauthenticated -> 401")
    class UnauthenticatedRejected {

        @Test
        @DisplayName("POST /api/products without token -> 401")
        void createProduct_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"price\":1.0,\"stock\":1}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/products/{id} without token -> 401")
        void updateProduct_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"price\":1.0,\"stock\":1}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/products/{id} without token -> 401")
        void deleteProduct_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/products/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/categories without token -> 401")
        void createCategory_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Cat\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/categories/{id} without token -> 401")
        void updateCategory_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/categories/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Cat\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/categories/{id} without token -> 401")
        void deleteCategory_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/orders/my without token -> 401")
        void getMyOrders_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/orders/my"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/cart without token -> 401")
        void getCart_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/cart"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/users/me without token -> 401")
        void getMe_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /actuator/metrics without token -> 401")
        void actuatorMetrics_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Admin-only endpoints — ROLE_USER -> 403")
    class NonAdminForbidden {

        @Test
        @DisplayName("POST /api/products as USER -> 403")
        void createProduct_asUser_returns403() throws Exception {
            mockMvc.perform(post("/api/products")
                            .with(asUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"price\":1.0,\"stock\":1}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/products/{id} as USER -> 403")
        void updateProduct_asUser_returns403() throws Exception {
            mockMvc.perform(put("/api/products/1")
                            .with(asUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"price\":1.0,\"stock\":1}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /api/products/{id} as USER -> 403")
        void deleteProduct_asUser_returns403() throws Exception {
            mockMvc.perform(delete("/api/products/1").with(asUser()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/categories as USER -> 403")
        void createCategory_asUser_returns403() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .with(asUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Cat\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/categories/{id} as USER -> 403")
        void updateCategory_asUser_returns403() throws Exception {
            mockMvc.perform(put("/api/categories/1")
                            .with(asUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Cat\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /api/categories/{id} as USER -> 403")
        void deleteCategory_asUser_returns403() throws Exception {
            mockMvc.perform(delete("/api/categories/1").with(asUser()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /actuator/metrics as USER -> 403")
        void actuatorMetrics_asUser_returns403() throws Exception {
            mockMvc.perform(get("/actuator/metrics").with(asUser()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/admin/** as USER -> 403")
        void adminEndpoint_asUser_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard").with(asUser()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Admin-only endpoints — ROLE_ADMIN passes the security gate")
    class AdminPermitted {

        @Test
        @DisplayName("POST /api/products as ADMIN -> not 403")
        void createProduct_asAdmin_passesSecurityGate() throws Exception {
            mockMvc.perform(post("/api/products")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Widget\",\"description\":\"desc\",\"price\":9.99,\"stock\":10}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("PUT /api/products/{id} as ADMIN -> not 403")
        void updateProduct_asAdmin_passesSecurityGate() throws Exception {
            mockMvc.perform(put("/api/products/1")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Widget\",\"description\":\"desc\",\"price\":9.99,\"stock\":10}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("DELETE /api/products/{id} as ADMIN -> not 403")
        void deleteProduct_asAdmin_passesSecurityGate() throws Exception {
            mockMvc.perform(delete("/api/products/1").with(asAdmin()))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("POST /api/categories as ADMIN -> not 403")
        void createCategory_asAdmin_passesSecurityGate() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Electronics\"}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("GET /actuator/metrics as ADMIN -> not 403")
        void actuatorMetrics_asAdmin_passesSecurityGate() throws Exception {
            mockMvc.perform(get("/actuator/metrics").with(asAdmin()))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    @Nested
    @DisplayName("User-scoped endpoints — any authenticated user passes the security gate")
    class AuthenticatedUserEndpoints {

        @Test
        @DisplayName("GET /api/orders/my as USER -> 200")
        void getMyOrders_asUser_passesSecurityGate() throws Exception {
            mockMvc.perform(get("/api/orders/my").with(asUser()))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/cart as USER -> 200")
        void getCart_asUser_passesSecurityGate() throws Exception {
            mockMvc.perform(get("/api/cart").with(asUser()))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("GET /api/users/me as USER -> 200")
        void getMe_asUser_passesSecurityGate() throws Exception {
            mockMvc.perform(get("/api/users/me").with(asUser()))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("Seller-permitted endpoints — ROLE_SELLER can create/update products, categories, and manage orders")
    class SellerPermitted {

        @Test
        @DisplayName("POST /api/products as SELLER -> not 403")
        void createProduct_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(post("/api/products")
                            .with(asSeller())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Widget\",\"description\":\"desc\",\"price\":9.99,\"stock\":10}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("PUT /api/products/{id} as SELLER -> not 403")
        void updateProduct_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(put("/api/products/1")
                            .with(asSeller())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Widget\",\"description\":\"desc\",\"price\":9.99,\"stock\":10}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("POST /api/categories as SELLER -> not 403")
        void createCategory_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .with(asSeller())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Electronics\"}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("PUT /api/categories/{id} as SELLER -> not 403")
        void updateCategory_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(put("/api/categories/1")
                            .with(asSeller())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Electronics\"}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("GET /api/orders as SELLER -> not 403")
        void getAllOrders_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(get("/api/orders").with(asSeller()))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("PUT /api/orders/{id}/status as SELLER -> not 403")
        void updateOrderStatus_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(put("/api/orders/1/status")
                            .with(asSeller())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SHIPPED\"}"))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("POST /api/products/{id}/image as SELLER -> not 403")
        void uploadProductImage_asSeller_passesSecurityGate() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", "fake".getBytes());
            mockMvc.perform(multipart("/api/products/1/image").file(file).with(asSeller()))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("DELETE /api/products/{id}/image as SELLER -> not 403")
        void removeProductImage_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(delete("/api/products/1/image").with(asSeller()))
                    .andExpect(status().is(not(equalTo(403))));
        }

        @Test
        @DisplayName("POST /api/orders/{id}/cancel as SELLER -> not 403")
        void cancelOrder_asSeller_passesSecurityGate() throws Exception {
            mockMvc.perform(post("/api/orders/1/cancel").with(asSeller()))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    @Nested
    @DisplayName("Seller-forbidden endpoints — ROLE_SELLER cannot delete or manage users")
    class SellerForbidden {

        @Test
        @DisplayName("DELETE /api/products/{id} as SELLER -> 403")
        void deleteProduct_asSeller_returns403() throws Exception {
            mockMvc.perform(delete("/api/products/1").with(asSeller()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /api/categories/{id} as SELLER -> 403")
        void deleteCategory_asSeller_returns403() throws Exception {
            mockMvc.perform(delete("/api/categories/1").with(asSeller()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/users as SELLER -> 403")
        void listUsers_asSeller_returns403() throws Exception {
            mockMvc.perform(get("/api/users").with(asSeller()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /api/users/{id} as SELLER -> 403")
        void deleteUser_asSeller_returns403() throws Exception {
            mockMvc.perform(delete("/api/users/1").with(asSeller()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/users/{id} as SELLER -> 403")
        void updateUser_asSeller_returns403() throws Exception {
            mockMvc.perform(put("/api/users/1").with(asSeller())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Jane\",\"email\":\"jane@example.com\",\"role\":\"CUSTOMER\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /actuator/metrics as SELLER -> 403")
        void actuatorMetrics_asSeller_returns403() throws Exception {
            mockMvc.perform(get("/actuator/metrics").with(asSeller()))
                    .andExpect(status().isForbidden());
        }
    }

}