package com.supportai.auth.service;

import com.supportai.auth.dto.ForgotPasswordRequest;
import com.supportai.auth.dto.ForgotPasswordResponse;
import com.supportai.auth.dto.ResetPasswordRequest;
import com.supportai.auth.dto.VerifyResetTokenResponse;
import com.supportai.auth.entity.PasswordResetToken;
import com.supportai.auth.repository.PasswordResetTokenRepository;
import com.supportai.common.email.EmailService;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);

        passwordResetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                emailService,
                "http://localhost:5173"
        );

        user = new User(
                "John Doe",
                "john@example.com",
                "encoded-old-password",
                Role.CUSTOMER
        );
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void forgotPassword_existingUser_generatesTokenAndSendsEmail() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.forgotPassword(
                new ForgotPasswordRequest("john@example.com")
        );

        assertNotNull(response);
        assertEquals(
                "If an account exists with this email address, a password reset link has been sent.",
                response.message()
        );

        verify(tokenRepository).invalidateActiveTokensForUser(eq(1L), any(Instant.class));
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("john@example.com"), contains("http://localhost:5173/reset-password?token="));
    }

    @Test
    void forgotPassword_nonExistentUser_returnsGenericMessageWithoutSendingEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.forgotPassword(
                new ForgotPasswordRequest("unknown@example.com")
        );

        assertNotNull(response);
        assertEquals(
                "If an account exists with this email address, a password reset link has been sent.",
                response.message()
        );

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void forgotPassword_cooldownActive_suppressesEmailAndTokenCreation() {
        PasswordResetToken recentToken = new PasswordResetToken(user, "hash", Instant.now().plus(15, ChronoUnit.MINUTES));
        ReflectionTestUtils.setField(recentToken, "createdAt", Instant.now().minus(20, ChronoUnit.SECONDS));

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(recentToken));

        ForgotPasswordResponse response = passwordResetService.forgotPassword(
                new ForgotPasswordRequest("john@example.com")
        );

        assertNotNull(response);
        assertEquals(
                "If an account exists with this email address, a password reset link has been sent.",
                response.message()
        );

        verify(tokenRepository, never()).invalidateActiveTokensForUser(anyLong(), any());
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void verifyResetToken_validToken_returnsValidTrue() {
        String rawToken = "test-raw-token-123";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        VerifyResetTokenResponse response = passwordResetService.verifyResetToken(rawToken);

        assertTrue(response.valid());
        assertEquals("Token is valid", response.message());
    }

    @Test
    void verifyResetToken_nonExistentToken_returnsValidFalse() {
        String rawToken = "nonexistent-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        VerifyResetTokenResponse response = passwordResetService.verifyResetToken(rawToken);

        assertFalse(response.valid());
        assertEquals("Invalid or expired password reset token", response.message());
    }

    @Test
    void verifyResetToken_usedToken_returnsValidFalse() {
        String rawToken = "used-raw-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        token.setUsedAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        VerifyResetTokenResponse response = passwordResetService.verifyResetToken(rawToken);

        assertFalse(response.valid());
        assertEquals("This password reset token has already been used", response.message());
    }

    @Test
    void verifyResetToken_expiredToken_returnsValidFalse() {
        String rawToken = "expired-raw-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        VerifyResetTokenResponse response = passwordResetService.verifyResetToken(rawToken);

        assertFalse(response.valid());
        assertEquals("This password reset token has expired", response.message());
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndMarksTokenUsed() {
        String rawToken = "valid-reset-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("NewPassword123", "encoded-old-password")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-new-password");

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");
        passwordResetService.resetPassword(request);

        assertEquals("encoded-new-password", user.getPassword());
        assertTrue(token.isUsed());
        assertNotNull(token.getUsedAt());

        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_sameAsCurrentPassword_throwsIllegalArgumentException() {
        String rawToken = "valid-reset-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("OldPassword123", "encoded-old-password")).thenReturn(true);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "OldPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("New password must be different from current password", ex.getMessage());
        assertFalse(token.isUsed());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_usedToken_throwsIllegalArgumentException() {
        String rawToken = "used-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        token.setUsedAt(Instant.now().minus(2, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("This password reset token has already been used", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_expiredToken_throwsIllegalArgumentException() {
        String rawToken = "expired-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("This password reset token has expired", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_nonExistentToken_throwsIllegalArgumentException() {
        String rawToken = "missing-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("Invalid or expired password reset token", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}
