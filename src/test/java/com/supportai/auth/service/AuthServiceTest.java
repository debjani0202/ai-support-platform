
package com.supportai.auth.service;

import com.supportai.auth.dto.AuthResponse;
import com.supportai.auth.dto.AuthResult;
import com.supportai.auth.dto.ChangePasswordRequest;
import com.supportai.auth.dto.LoginRequest;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.dto.RegisterRequest;
import com.supportai.auth.repository.RefreshTokenRepository;
import com.supportai.security.jwt.JwtService;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

        private UserRepository userRepository;
        private PasswordEncoder passwordEncoder;
        private JwtService jwtService;
        private RefreshTokenService refreshTokenService;
        private RefreshTokenRepository refreshTokenRepository;
        private EntityManager entityManager;
        private AuthService authService;

        private User user;

        @BeforeEach
        void setUp() {

                userRepository = mock(UserRepository.class);
                passwordEncoder = mock(PasswordEncoder.class);
                jwtService = mock(JwtService.class);
                refreshTokenService = mock(RefreshTokenService.class);
                refreshTokenRepository = mock(RefreshTokenRepository.class);
                entityManager = mock(EntityManager.class);

                authService = new AuthService(
                                userRepository,
                                passwordEncoder,
                                jwtService,
                                refreshTokenService,
                                refreshTokenRepository,
                                entityManager);

                user = new User(
                                "Test User",
                                "test@example.com",
                                "encoded-old-password",
                                Role.CUSTOMER);

                ReflectionTestUtils.setField(user, "id", 1L);
        }

        // --------------------------------------------------
        // CHANGE PASSWORD TESTS
        // --------------------------------------------------

        @Test
        void changePasswordShouldUpdatePasswordHash() {

                when(userRepository.findById(1L))
                                .thenReturn(Optional.of(user));

                when(passwordEncoder.matches(
                                "OldPassword123",
                                "encoded-old-password")).thenReturn(true);

                when(passwordEncoder.matches(
                                "NewPassword123",
                                "encoded-old-password")).thenReturn(false);

                when(passwordEncoder.encode("NewPassword123"))
                                .thenReturn("encoded-new-password");

                ChangePasswordRequest request = new ChangePasswordRequest(
                                "OldPassword123",
                                "NewPassword123");

                authService.changePassword(1L, request);

                assertEquals(
                                "encoded-new-password",
                                user.getPassword());

                verify(passwordEncoder).encode("NewPassword123");
                verify(userRepository).findById(1L);
                verify(refreshTokenRepository).lockUserTokens(1L);

                verify(refreshTokenRepository)
                                .revokeAllByUserId(eq(1L), any(Instant.class));
        }

        @Test
        void changePasswordShouldRejectIncorrectCurrentPassword() {

                when(userRepository.findById(1L))
                                .thenReturn(Optional.of(user));

                when(passwordEncoder.matches(
                                "WrongPassword123",
                                "encoded-old-password")).thenReturn(false);

                ChangePasswordRequest request = new ChangePasswordRequest(
                                "WrongPassword123",
                                "NewPassword123");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.changePassword(1L, request));

                assertEquals(
                                "encoded-old-password",
                                user.getPassword());

                verify(passwordEncoder, never())
                                .encode(anyString());
        }

        @Test
        void changePasswordShouldRejectSamePassword() {

                when(userRepository.findById(1L))
                                .thenReturn(Optional.of(user));

                when(passwordEncoder.matches(
                                "OldPassword123",
                                "encoded-old-password")).thenReturn(true);

                ChangePasswordRequest request = new ChangePasswordRequest(
                                "OldPassword123",
                                "OldPassword123");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.changePassword(1L, request));

                assertEquals(
                                "encoded-old-password",
                                user.getPassword());

                verify(passwordEncoder, never())
                                .encode(anyString());
        }

        @Test
        void changePasswordShouldRejectNonexistentUser() {

                when(userRepository.findById(999L))
                                .thenReturn(Optional.empty());

                ChangePasswordRequest request = new ChangePasswordRequest(
                                "OldPassword123",
                                "NewPassword123");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.changePassword(999L, request));

                verify(passwordEncoder, never())
                                .encode(anyString());
        }

        @Test
        void changePasswordShouldNotRevokeTokensWhenCurrentPasswordIsIncorrect() {

                when(userRepository.findById(1L))
                                .thenReturn(Optional.of(user));

                when(passwordEncoder.matches(
                                "WrongPassword123",
                                "encoded-old-password")).thenReturn(false);

                ChangePasswordRequest request = new ChangePasswordRequest(
                                "WrongPassword123",
                                "NewPassword123");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.changePassword(1L, request));

                // Lock is acquired before password verification.
                verify(refreshTokenRepository).lockUserTokens(1L);

                // But existing refresh tokens must not be revoked.
                verify(refreshTokenRepository, never())
                                .revokeAllByUserId(anyLong(), any(Instant.class));

                assertEquals(
                                "encoded-old-password",
                                user.getPassword());

                verify(passwordEncoder, never())
                                .encode(anyString());
        }

        // --------------------------------------------------
        // REGISTRATION TEST
        // --------------------------------------------------

        @Test
        void registerShouldSaveUserAndReturnAuthResponseWithUserId() {

                when(userRepository.existsByEmail("jane@example.com"))
                                .thenReturn(false);

                when(passwordEncoder.encode("Password123"))
                                .thenReturn("encoded-password");

                User savedMockUser = mock(User.class);

                when(savedMockUser.getId()).thenReturn(1L);
                when(savedMockUser.getName()).thenReturn("Jane Doe");
                when(savedMockUser.getEmail()).thenReturn("jane@example.com");
                when(savedMockUser.getRole()).thenReturn(Role.CUSTOMER);

                when(userRepository.save(any(User.class)))
                                .thenReturn(savedMockUser);

                RegisterRequest request = new RegisterRequest(
                                "Jane Doe",
                                "jane@example.com",
                                "Password123");

                AuthResponse response = authService.register(request);

                assertNotNull(response);
                assertEquals(1L, response.userId());
                assertEquals("Jane Doe", response.name());
                assertEquals("jane@example.com", response.email());
                assertEquals(Role.CUSTOMER, response.role());

                verify(userRepository).save(any(User.class));
        }

        // --------------------------------------------------
        // LOGIN TESTS
        // --------------------------------------------------

        @Test
        void loginShouldReturnAuthResultWithUserIdAndTokens() {

                User mockUser = mock(User.class);

                when(mockUser.getId()).thenReturn(1L);
                when(mockUser.getName()).thenReturn("Jane Doe");
                when(mockUser.getEmail()).thenReturn("jane@example.com");
                when(mockUser.getPassword()).thenReturn("encoded-password");
                when(mockUser.getRole()).thenReturn(Role.CUSTOMER);

                when(userRepository.findByEmail("jane@example.com"))
                                .thenReturn(Optional.of(mockUser));

                when(passwordEncoder.matches(
                                "Password123",
                                "encoded-password")).thenReturn(true);

                LoginResponse loginResponse = new LoginResponse(
                                1L,
                                "Jane Doe",
                                "jane@example.com",
                                Role.CUSTOMER,
                                "generated-jwt-token",
                                "Bearer");

                AuthResult authResult = new AuthResult(
                                loginResponse,
                                "raw-refresh-token-123");

                when(refreshTokenService.createRefreshToken(mockUser))
                                .thenReturn(authResult);

                LoginRequest request = new LoginRequest(
                                "jane@example.com",
                                "Password123");

                AuthResult result = authService.login(request);

                assertNotNull(result);
                assertEquals(1L, result.loginResponse().userId());
                assertEquals("Jane Doe", result.loginResponse().name());
                assertEquals("jane@example.com", result.loginResponse().email());
                assertEquals(Role.CUSTOMER, result.loginResponse().role());
                assertEquals(
                                "generated-jwt-token",
                                result.loginResponse().accessToken());
                assertEquals("Bearer", result.loginResponse().tokenType());
                assertEquals("raw-refresh-token-123", result.rawRefreshToken());

                InOrder inOrder = inOrder(
                                passwordEncoder,
                                refreshTokenRepository,
                                entityManager,
                                refreshTokenService);

                inOrder.verify(passwordEncoder)
                                .matches("Password123", "encoded-password");

                inOrder.verify(refreshTokenRepository)
                                .lockUserTokens(1L);

                inOrder.verify(entityManager)
                                .refresh(mockUser);

                inOrder.verify(refreshTokenService)
                                .createRefreshToken(mockUser);
        }

        @Test
        void loginShouldAbortWhenPasswordChangedConcurrentlyUnderLock() {

                User mockUser = mock(User.class);

                when(mockUser.getId()).thenReturn(1L);
                when(mockUser.getEmail()).thenReturn("jane@example.com");
                when(mockUser.getPassword()).thenReturn("old-hash");

                when(userRepository.findByEmail("jane@example.com"))
                                .thenReturn(Optional.of(mockUser));

                when(passwordEncoder.matches(
                                "Password123",
                                "old-hash")).thenReturn(true);

                // Simulate a concurrent password update during entity refresh.
                doAnswer(invocation -> {
                        when(mockUser.getPassword()).thenReturn("new-hash");
                        return null;
                }).when(entityManager).refresh(mockUser);

                LoginRequest request = new LoginRequest(
                                "jane@example.com",
                                "Password123");

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.login(request));

                assertEquals(
                                "Invalid email or password",
                                exception.getMessage());

                verify(refreshTokenService, never())
                                .createRefreshToken(any());
        }

        @Test
        void loginShouldRejectIncorrectPasswordWithoutCreatingRefreshToken() {

                User mockUser = mock(User.class);

                when(mockUser.getPassword()).thenReturn("encoded-password");

                when(userRepository.findByEmail("jane@example.com"))
                                .thenReturn(Optional.of(mockUser));

                when(passwordEncoder.matches(
                                "WrongPassword123",
                                "encoded-password")).thenReturn(false);

                LoginRequest request = new LoginRequest(
                                "jane@example.com",
                                "WrongPassword123");

                assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.login(request));

                verify(refreshTokenRepository, never())
                                .lockUserTokens(any());

                verify(refreshTokenService, never())
                                .createRefreshToken(any());

                verify(entityManager, never())
                                .refresh(any());
        }
}
