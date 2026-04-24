package com.buyapi.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.buyapi.dto.request.AuthRequest;
import com.buyapi.dto.response.Responses.AuthResponse;
import com.buyapi.entity.Cart;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.repository.CartRepository;
import com.buyapi.repository.UserRepository;
import com.buyapi.security.JwtUtils;
import com.buyapi.service.impl.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;
    @Mock JwtUtils jwtUtils;

    @InjectMocks AuthService authService;

    private AuthRequest.Register registerRequest;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        registerRequest = new AuthRequest.Register("test@example.com", "password123", "Test User", null);
    }

    private UsernamePasswordAuthenticationToken stubAuthManager(String email, String role) {
        UserDetails springUser = new org.springframework.security.core.userdetails.User(
                email, "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(springUser, null, springUser.getAuthorities());
        when(authManager.authenticate(any())).thenReturn(token);
        return token;
    }

    private void stubSaveUser() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtils.generateToken(any())).thenReturn("jwt-token");
    }

    @Test
    void register_noRoleProvided_defaultsToCustomer() {
        stubSaveUser();
        stubAuthManager("test@example.com", "CUSTOMER");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void register_roleProvidedByUnauthenticatedCaller_isIgnoredDefaultsToCustomer() {
        stubSaveUser();
        stubAuthManager("test@example.com", "CUSTOMER");

        AuthRequest.Register req = new AuthRequest.Register(
                "test@example.com", "password123", "Test User", "ADMIN");

        AuthResponse response = authService.register(req);

        assertThat(response.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void register_roleProvidedByAdmin_isHonoured() {
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                "admin@example.com", "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        UsernamePasswordAuthenticationToken adminAuth =
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        stubSaveUser();
        stubAuthManager("test@example.com", "SELLER");

        AuthRequest.Register req = new AuthRequest.Register(
                "test@example.com", "password123", "Test User", "SELLER");

        AuthResponse response = authService.register(req);

        assertThat(response.role()).isEqualTo("SELLER");
    }

    @Test
    void register_invalidRoleProvidedByAdmin_throwsBadRequest() {
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                "admin@example.com", "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        UsernamePasswordAuthenticationToken adminAuth =
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        AuthRequest.Register req = new AuthRequest.Register(
                "test@example.com", "password123", "Test User", "SUPERUSER");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    void register_duplicateEmail_throwsBadRequest() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void login_validCredentials_returnsToken() {
        User user = User.builder()
                .id(1L).email("test@example.com").password("hashed")
                .fullName("Test User").role(User.Role.CUSTOMER).build();

        stubAuthManager("test@example.com", "CUSTOMER");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = authService.login(new AuthRequest.Login("test@example.com", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("test@example.com");
    }
}