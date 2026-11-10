package com.supportai.auth.service;

import com.supportai.auth.dto.ForgotPasswordRequest;
import com.supportai.auth.dto.ForgotPasswordResponse;
import com.supportai.auth.dto.ResetPasswordRequest;
import com.supportai.auth.dto.VerifyResetTokenResponse;
import com.supportai.auth.entity.PasswordResetToken;
import com.supportai.auth.repository.PasswordResetTokenRepository;
import com.supportai.auth.repository.RefreshTokenRepository;
import com.supportai.common.email.EmailService;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private EntityManager entityManager;
    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        entityManager = mock(EntityManager.class);

        passwordResetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailService,
                entityManager,
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

        InOrder inOrder = inOrder(refreshTokenRepository, tokenRepository, emailService);
        inOrder.verify(refreshTokenRepository).lockUserTokens(1L);
        inOrder.verify(tokenRepository).findFirstByUserIdOrderByCreatedAtDesc(1L);
        inOrder.verify(tokenRepository).invalidateActiveTokensForUser(eq(1L), any(Instant.class));
        inOrder.verify(tokenRepository).save(any(PasswordResetToken.class));
        inOrder.verify(emailService).sendPasswordResetEmail(eq("john@example.com"), contains("http://localhost:5173/reset-password?token="));
    }

    @Test
    void forgotPassword_nonExistentUser_returnsGenericMessageWithoutSendingEmailOrLocking() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.forgotPassword(
                new ForgotPasswordRequest("unknown@example.com")
        );

        assertNotNull(response);
        assertEquals(
                "If an account exists with this email address, a password reset link has been sent.",
                response.message()
        );

        verify(refreshTokenRepository, never()).lockUserTokens(anyLong());
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

        verify(refreshTokenRepository).lockUserTokens(1L);
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
    void resetPassword_validToken_updatesPasswordMarksTokenUsedAndRevokesRefreshTokens() {
        String rawToken = "valid-reset-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("NewPassword123", "encoded-old-password")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-new-password");

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");
        passwordResetService.resetPassword(request);

        assertEquals("encoded-new-password", user.getPassword());
        assertTrue(token.isUsed());
        assertNotNull(token.getUsedAt());

        verify(refreshTokenRepository).lockUserTokens(1L);
        verify(entityManager).refresh(token);
        verify(entityManager).refresh(user);
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(eq(1L), any(Instant.class));
        verify(tokenRepository).save(token);
        verify(entityManager, times(2)).flush();
    }

    @Test
    void resetPassword_acquiresUserAdvisoryLockBeforePessimisticTokenLookup() {
        String rawToken = "valid-reset-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("NewPassword123", "encoded-old-password")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-new-password");

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");
        passwordResetService.resetPassword(request);

        InOrder inOrder = inOrder(tokenRepository, refreshTokenRepository, entityManager, userRepository);
        inOrder.verify(tokenRepository).findByTokenHash(tokenHash);
        inOrder.verify(refreshTokenRepository).lockUserTokens(1L);
        inOrder.verify(tokenRepository).findByTokenHashWithLock(tokenHash);
        inOrder.verify(entityManager).refresh(token);
        inOrder.verify(entityManager).refresh(user);
        inOrder.verify(userRepository).save(user);
        inOrder.verify(entityManager).flush();
        inOrder.verify(refreshTokenRepository).revokeAllByUserId(eq(1L), any(Instant.class));
        inOrder.verify(tokenRepository).save(token);
        inOrder.verify(entityManager).flush();
    }

    @Test
    void resetPassword_sameAsCurrentPassword_throwsIllegalArgumentExceptionWithoutRevokingTokens() {
        String rawToken = "valid-reset-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("OldPassword123", "encoded-old-password")).thenReturn(true);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "OldPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("New password must be different from current password", ex.getMessage());
        assertFalse(token.isUsed());
        verify(refreshTokenRepository).lockUserTokens(1L);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void resetPassword_usedToken_throwsIllegalArgumentExceptionWithoutRevokingTokens() {
        String rawToken = "used-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        token.setUsedAt(Instant.now().minus(2, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("This password reset token has already been used", ex.getMessage());
        verify(refreshTokenRepository).lockUserTokens(1L);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void resetPassword_tokenAlreadyUsedAfterLockAcquisition_throwsException() {
        String rawToken = "race-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));

        // Simulate another transaction marking the token as used while we waited for the advisory lock
        doAnswer(invocation -> {
            token.setUsedAt(Instant.now());
            return null;
        }).when(entityManager).refresh(token);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("This password reset token has already been used", ex.getMessage());
        verify(refreshTokenRepository).lockUserTokens(1L);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void resetPassword_expiredToken_throwsIllegalArgumentExceptionWithoutRevokingTokens() {
        String rawToken = "expired-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("This password reset token has expired", ex.getMessage());
        verify(refreshTokenRepository).lockUserTokens(1L);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void resetPassword_nonExistentToken_throwsIllegalArgumentExceptionWithoutLocking() {
        String rawToken = "missing-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("Invalid or expired password reset token", ex.getMessage());
        verify(refreshTokenRepository, never()).lockUserTokens(anyLong());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void resetPassword_tokenUserMismatchAfterLock_throwsIllegalArgumentException() {
        String rawToken = "mismatch-token";
        String tokenHash = passwordResetService.hashToken(rawToken);

        User initialUser = new User("User 1", "user1@example.com", "pass", Role.CUSTOMER);
        ReflectionTestUtils.setField(initialUser, "id", 1L);

        User competingUser = new User("User 2", "user2@example.com", "pass", Role.CUSTOMER);
        ReflectionTestUtils.setField(competingUser, "id", 2L);

        PasswordResetToken initialToken = new PasswordResetToken(initialUser, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));
        PasswordResetToken lockedToken = new PasswordResetToken(competingUser, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(initialToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(lockedToken));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(request)
        );

        assertEquals("Invalid or expired password reset token", ex.getMessage());
        verify(refreshTokenRepository).lockUserTokens(1L);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void resetPassword_blankOrNullToken_throwsIllegalArgumentExceptionImmediately() {
        assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(new ResetPasswordRequest(null, "NewPassword123"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(new ResetPasswordRequest("   ", "NewPassword123"))
        );

        verify(refreshTokenRepository, never()).lockUserTokens(anyLong());
        verify(tokenRepository, never()).findByTokenHash(anyString());
    }
}
