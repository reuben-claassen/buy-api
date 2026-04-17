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

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(User.Role.CUSTOMER)
                .build();

        userRepository.save(user);

        Cart cart = Cart.builder().user(user).build();
        cartRepository.save(cart);

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name());
    }

    public AuthResponse login(AuthRequest.Login request) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails details = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByEmail(details.getUsername()).orElseThrow();
        String token = jwtUtils.generateToken(details);
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
