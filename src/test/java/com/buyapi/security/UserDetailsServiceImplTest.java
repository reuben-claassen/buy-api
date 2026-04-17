package com.buyapi.security;

import com.buyapi.entity.User;
import com.buyapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserDetailsServiceImpl userDetailsService;

    private User sampleUser(String email, User.Role role) {
        return User.builder()
                .id(1L).email(email).password("hashed_password")
                .fullName("Test User").role(role).enabled(true).build();
    }

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(sampleUser("user@example.com", User.Role.CUSTOMER)));

        var details = userDetailsService.loadUserByUsername("user@example.com");

        assertThat(details.getUsername()).isEqualTo("user@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed_password");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void loadUserByUsername_adminUser_hasAdminAuthority() {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(sampleUser("admin@example.com", User.Role.ADMIN)));

        var details = userDetailsService.loadUserByUsername("admin@example.com");

        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_unknownEmail_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost@example.com");
    }
}
