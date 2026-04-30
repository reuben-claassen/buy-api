package com.buyapi.config;

import java.util.ArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.buyapi.security.JwtUtils;

import jakarta.annotation.PostConstruct;

/**
 * Security configuration for {@code @WebMvcTest} slices.
 *
 * JwtAuthFilter is excluded so tests can use {@code @WithMockUser} without
 * interference. {@code @EnableMethodSecurity} activates {@code @PreAuthorize}
 * without loading the full SecurityFilterChain. AuthenticationPrincipalArgumentResolver
 * is registered early so {@code @AuthenticationPrincipal} resolves correctly in the
 * test slice.
 */
@TestConfiguration
@EnableMethodSecurity
public class WebMvcTestSecurityConfig {

    private final RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    public WebMvcTestSecurityConfig(RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        this.requestMappingHandlerAdapter = requestMappingHandlerAdapter;
    }

    @PostConstruct
    public void insertAuthenticationPrincipalResolver() {
        var existing = requestMappingHandlerAdapter.getArgumentResolvers();
        var updated = new ArrayList<>();
        updated.add(new AuthenticationPrincipalArgumentResolver());
        if (existing != null) {
            updated.addAll(existing);
        }
        requestMappingHandlerAdapter.setArgumentResolvers(
                updated.stream()
                        .map(org.springframework.web.method.support.HandlerMethodArgumentResolver.class::cast)
                        .toList()
        );
    }

    @Bean
    public JwtUtils jwtUtils() {
        return org.mockito.Mockito.mock(JwtUtils.class);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return org.mockito.Mockito.mock(UserDetailsService.class);
    }
}