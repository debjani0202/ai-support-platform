
package com.supportai.auth.service;

import com.supportai.auth.dto.ChangePasswordRequest;
import com.supportai.security.jwt.JwtService;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService
        );

        user = new User(
                "Test User",
                "test@example.com",
                "encoded-old-password",
                Role.CUSTOMER
        );
    }

    @Test
    void changePasswordShouldUpdatePasswordHash() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "OldPassword123",
                "encoded-old-password"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "NewPassword123",
                "encoded-old-password"
        )).thenReturn(false);

        when(passwordEncoder.encode("NewPassword123"))
                .thenReturn("encoded-new-password");

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "NewPassword123"
        );

        authService.changePassword(1L, request);

        assertEquals(
                "encoded-new-password",
                user.getPassword()
        );

        verify(passwordEncoder).encode("NewPassword123");
        verify(userRepository).findById(1L);
    }

    @Test
    void changePasswordShouldRejectIncorrectCurrentPassword() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongPassword123",
                "encoded-old-password"
        )).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongPassword123",
                "NewPassword123"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.changePassword(1L, request)
        );

        assertEquals(
                "encoded-old-password",
                user.getPassword()
        );

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    @Test
    void changePasswordShouldRejectSamePassword() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "OldPassword123",
                "encoded-old-password"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "OldPassword123",
                "encoded-old-password"
        )).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "OldPassword123"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.changePassword(1L, request)
        );

        assertEquals(
                "encoded-old-password",
                user.getPassword()
        );

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    @Test
    void changePasswordShouldRejectNonexistentUser() {
        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123",
                "NewPassword123"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.changePassword(999L, request)
        );

        verify(passwordEncoder, never())
                .encode(anyString());
    }
}
