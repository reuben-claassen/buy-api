package com.buyapi.config;

import com.buyapi.security.JwtUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;

/**
 * Security configuration for @WebMvcTest slices.
 *
 * Notes: - JwtAuthFilter is intentionally excluded. Tests use @WithMockUser /
 * .with(user(...)), and including the filter interferes with SecurityContext
 * propagation.
 *
 * - @EnableMethodSecurity is required to activate @PreAuthorize without loading
 * the full SecurityFilterChain.
 *
 * - AuthenticationPrincipalArgumentResolver is inserted at the start of the
 * argument resolver list. In Spring Boot 4, @WebMvcTest no longer registers it
 * early enough, causing @AuthenticationPrincipal to resolve incorrectly.
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
