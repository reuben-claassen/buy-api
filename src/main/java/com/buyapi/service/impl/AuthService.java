package com.buyapi.service.impl;

import com.buyapi.dto.request.AuthRequest;
import com.buyapi.dto.response.Responses.AuthResponse;
import com.buyapi.entity.Cart;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.repository.CartRepository;
import com.buyapi.repository.UserRepository;
import com.buyapi.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;

    @Transactional
    public AuthResponse register(AuthRequest.Register request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already in use: " + request.email());
        }

        User.Role role = resolveRole(request.role());

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(role)
                .build();

        userRepository.save(user);

        Cart cart = Cart.builder().user(user).build();
        cartRepository.save(cart);

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    public AuthResponse login(AuthRequest.Login request) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails details = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByEmail(details.getUsername()).orElseThrow();
        String token = jwtUtils.generateToken(details);
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    private User.Role resolveRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return User.Role.CUSTOMER;
        }

        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        boolean callerIsAdmin = caller != null && caller.isAuthenticated()
                && caller.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(a -> a.equals("ROLE_ADMIN"));

        if (!callerIsAdmin) {
            return User.Role.CUSTOMER;
        }

        try {
            return User.Role.valueOf(requestedRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + requestedRole + ". Valid values: CUSTOMER, SELLER, ADMIN");
        }
    }
}