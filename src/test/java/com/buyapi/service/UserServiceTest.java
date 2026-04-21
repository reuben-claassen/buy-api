package com.buyapi.service;

import com.buyapi.dto.response.Responses.PageResponse;
import com.buyapi.dto.response.Responses.UserResponse;
import com.buyapi.entity.User;
import com.buyapi.exception.BadRequestException;
import com.buyapi.exception.ResourceNotFoundException;
import com.buyapi.repository.UserRepository;
import com.buyapi.service.impl.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserService userService;

    private User sampleUser(Long id, String email) {
        return User.builder()
                .id(id).email(email).fullName("Test User")
                .role(User.Role.CUSTOMER).enabled(true)
                .createdAt(Instant.now()).build();
    }

    @Test
    void getMe_existingUser_returnsResponse() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        UserResponse response = userService.getMe("alice@example.com");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void getMe_unknownEmail_throwsNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe("ghost@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsPagedResults() {
        User user = sampleUser(1L, "alice@example.com");
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);

        PageResponse<UserResponse> result = userService.getAll(pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void getAll_emptyRepository_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(userRepository.findAll(pageable)).thenReturn(emptyPage);

        PageResponse<UserResponse> result = userService.getAll(pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    void getAll_multiplePages_returnsCorrectPageMetadata() {
        List<User> users = List.of(sampleUser(1L, "a@example.com"), sampleUser(2L, "b@example.com"));
        Pageable pageable = PageRequest.of(0, 2);
        Page<User> page = new PageImpl<>(users, pageable, 5);
        when(userRepository.findAll(pageable)).thenReturn(page);

        PageResponse<UserResponse> result = userService.getAll(pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void getById_existingUser_returnsResponse() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Test User");
    }

    @Test
    void getById_missingUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUser_existingUser_deletesSuccessfully() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_missingUser_throwsNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_doesNotDeleteOtherUsers() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
        verify(userRepository, never()).deleteById(argThat(id -> !id.equals(1L)));
    }

    @Test
    void changeRole_validRole_updatesAndReturnsUser() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.changeRole(1L, "SELLER");

        assertThat(response.role()).isEqualTo("SELLER");
        verify(userRepository).save(user);
    }

    @Test
    void changeRole_toAdmin_updatesRole() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.changeRole(1L, "ADMIN");

        assertThat(response.role()).isEqualTo("ADMIN");
    }

    @Test
    void changeRole_lowercaseInput_isAccepted() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.changeRole(1L, "seller");

        assertThat(response.role()).isEqualTo("SELLER");
    }

    @Test
    void changeRole_invalidRole_throwsBadRequest() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeRole(1L, "SUPERUSER"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    void changeRole_unknownUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(99L, "SELLER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changeRole_doesNotSaveOnInvalidRole() {
        User user = sampleUser(1L, "alice@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeRole(1L, "INVALID"));

        verify(userRepository, never()).save(any());
    }

}
