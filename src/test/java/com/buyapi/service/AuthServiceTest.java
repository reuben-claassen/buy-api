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
        registerRequest = new AuthRequest.Register("test@example.com", "password123", "Test User");
    }

    @Test
    void register_createsUserAndCart_returnsToken() {
        UserDetails springUser = new org.springframework.security.core.userdetails.User(
                "test@example.com", "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(springUser, null, springUser.getAuthorities());

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authManager.authenticate(any())).thenReturn(authToken);
        when(jwtUtils.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        verify(cartRepository).save(any(Cart.class));
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

        UserDetails springUser = new org.springframework.security.core.userdetails.User(
                "test@example.com", "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(springUser, null, springUser.getAuthorities());

        when(authManager.authenticate(any())).thenReturn(authToken);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = authService.login(new AuthRequest.Login("test@example.com", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("test@example.com");
    }
}
