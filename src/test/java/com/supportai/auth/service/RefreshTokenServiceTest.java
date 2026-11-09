package com.supportai.auth.service;

import com.supportai.auth.dto.AuthResult;
import com.supportai.auth.entity.RefreshToken;
import com.supportai.auth.repository.RefreshTokenRepository;
import com.supportai.exception.InvalidRefreshTokenException;
import com.supportai.exception.TokenAlreadyRotatedException;
import com.supportai.security.jwt.JwtService;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository tokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private User user;
    private Instant now;
    private Clock fixedClock;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-08T12:00:00.000Z");
        fixedClock = Clock.fixed(now, ZoneId.of("UTC"));

        user = new User("Alice", "alice@example.com", "hashed-password", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 100L);

        refreshTokenService = new RefreshTokenService(
                tokenRepository,
                jwtService,
                transactionManager,
                7L,
                fixedClock
        );
    }

    private void mockTransactionCommit() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    @DisplayName("Constructor accepts positive expiration days")
    void constructor_withPositiveExpirationDays_instantiatesSuccessfully() {
        assertDoesNotThrow(() -> new RefreshTokenService(
                tokenRepository,
                jwtService,
                transactionManager,
                1L,
                fixedClock
        ));
    }

    @Test
    @DisplayName("Constructor rejects zero expiration days")
    void constructor_withZeroExpirationDays_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshTokenService(
                        tokenRepository,
                        jwtService,
                        transactionManager,
                        0L,
                        fixedClock
                )
        );
        assertEquals(
                "Refresh token expiration days must be greater than zero, got: 0",
                ex.getMessage()
        );
    }

    @Test
    @DisplayName("Constructor rejects negative expiration days")
    void constructor_withNegativeExpirationDays_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshTokenService(
                        tokenRepository,
                        jwtService,
                        transactionManager,
                        -5L,
                        fixedClock
                )
        );
        assertEquals(
                "Refresh token expiration days must be greater than zero, got: -5",
                ex.getMessage()
        );
    }

    @Test
    @DisplayName("Token generation and SHA-256 hashing format and length")
    void tokenGenerationAndHashing_producesValidHexOutputs() {
        String rawToken = refreshTokenService.generateRawToken();
        assertNotNull(rawToken);
        assertEquals(64, rawToken.length());
        assertTrue(rawToken.matches("^[0-9a-f]{64}$"));

        String hash = refreshTokenService.hashToken(rawToken);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"));

        // Deterministic hashing
        assertEquals(hash, refreshTokenService.hashToken(rawToken));

        // Different tokens produce different hashes
        String anotherToken = refreshTokenService.generateRawToken();
        assertNotEquals(rawToken, anotherToken);
        assertNotEquals(hash, refreshTokenService.hashToken(anotherToken));
    }

    @Test
    @DisplayName("Create refresh token creates valid entity with UUID family and 7-day expiration")
    void createRefreshToken_createsValidToken() {
        when(jwtService.generateToken(user)).thenReturn("access-token-xyz");

        AuthResult authResult = refreshTokenService.createRefreshToken(user);

        assertNotNull(authResult);
        assertNotNull(authResult.rawRefreshToken());
        assertEquals(64, authResult.rawRefreshToken().length());
        assertEquals("access-token-xyz", authResult.loginResponse().accessToken());
        assertEquals(100L, authResult.loginResponse().userId());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals(refreshTokenService.hashToken(authResult.rawRefreshToken()), saved.getTokenHash());
        assertNotNull(saved.getTokenFamily());
        assertEquals(36, saved.getTokenFamily().length());
        assertEquals(now.plus(Duration.ofDays(7)), saved.getExpiresAt());
        assertFalse(saved.isRevoked());
        assertTrue(saved.isActive(now));
    }

    @Test
    @DisplayName("Successful rotation rotates token atomically and respects lock order: User -> Family -> Row")
    void rotateRefreshToken_success() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";

        RefreshToken activeToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(activeToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(activeToken));
        when(jwtService.generateToken(user)).thenReturn("new-access-token");

        AuthResult result = refreshTokenService.rotateRefreshToken(rawToken);

        assertNotNull(result);
        assertEquals("new-access-token", result.loginResponse().accessToken());
        assertNotNull(result.rawRefreshToken());
        assertNotEquals(rawToken, result.rawRefreshToken());

        // Verify activeToken was marked rotated
        assertTrue(activeToken.isRevoked());
        assertTrue(activeToken.isRotated());
        assertEquals(now, activeToken.getRevokedAt());
        assertNotNull(activeToken.getReplacedByTokenHash());

        // Verify successor token was saved
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository, times(2)).save(captor.capture());

        RefreshToken successor = captor.getAllValues().get(1);
        assertEquals(user, successor.getUser());
        assertEquals(family, successor.getTokenFamily());
        assertEquals(refreshTokenService.hashToken(result.rawRefreshToken()), successor.getTokenHash());
        assertEquals(activeToken.getReplacedByTokenHash(), successor.getTokenHash());
        assertEquals(now.plus(Duration.ofDays(7)), successor.getExpiresAt());

        // Verify strict lock hierarchy: User -> Family -> Row -> Commit
        InOrder inOrder = inOrder(tokenRepository, transactionManager);
        inOrder.verify(tokenRepository).lockUserTokens(100L);
        inOrder.verify(tokenRepository).lockTokenFamily(family);
        inOrder.verify(tokenRepository).findByTokenHashWithLock(tokenHash);
        inOrder.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("Blank or null raw token throws InvalidRefreshTokenException before transaction")
    void rotateRefreshToken_blankToken_throwsInvalidRefreshTokenException() {
        InvalidRefreshTokenException ex1 = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(null)
        );
        assertEquals(InvalidRefreshTokenException.Reason.INVALID_TOKEN, ex1.getReason());

        InvalidRefreshTokenException ex2 = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken("   ")
        );
        assertEquals(InvalidRefreshTokenException.Reason.INVALID_TOKEN, ex2.getReason());

        verifyNoInteractions(transactionManager);
    }

    @Test
    @DisplayName("Unknown token hash throws InvalidRefreshTokenException")
    void rotateRefreshToken_unknownToken_throwsInvalidRefreshTokenException() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.INVALID_TOKEN, ex.getReason());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("Expired token throws InvalidRefreshTokenException with TOKEN_EXPIRED reason")
    void rotateRefreshToken_expiredToken_throwsInvalidRefreshTokenException() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";

        RefreshToken expiredToken = new RefreshToken(user, tokenHash, family, now.minusSeconds(1));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(expiredToken));

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.TOKEN_EXPIRED, ex.getReason());
        verify(tokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("Rotated token within 5-second grace period returns TokenAlreadyRotatedException without family revocation")
    void rotateRefreshToken_withinGracePeriod_throwsTokenAlreadyRotatedException() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";
        String successorHash = "successor-hash-abc";

        RefreshToken rotatedToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        rotatedToken.setRevokedAt(now.minusSeconds(2)); // 2 seconds ago (< 5s grace)
        rotatedToken.setReplacedByTokenHash(successorHash);

        RefreshToken successor = new RefreshToken(user, successorHash, family, now.plus(Duration.ofDays(7)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHash(successorHash)).thenReturn(Optional.of(successor));
        when(tokenRepository.hasActiveTokenInFamily(family, now)).thenReturn(true);

        TokenAlreadyRotatedException ex = assertThrows(
                TokenAlreadyRotatedException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals("TOKEN_ALREADY_ROTATED", ex.getErrorCode());
        verify(tokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("Exact 5-second grace period boundary: within grace window")
    void rotateRefreshToken_exactFiveSecondGraceBoundary_withinGrace() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";
        String successorHash = "successor-hash-abc";

        RefreshToken rotatedToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        // Exact 5 seconds ago: now.minus(GRACE_PERIOD)
        rotatedToken.setRevokedAt(now.minusSeconds(5));
        rotatedToken.setReplacedByTokenHash(successorHash);

        RefreshToken successor = new RefreshToken(user, successorHash, family, now.plus(Duration.ofDays(7)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHash(successorHash)).thenReturn(Optional.of(successor));
        when(tokenRepository.hasActiveTokenInFamily(family, now)).thenReturn(true);

        TokenAlreadyRotatedException ex = assertThrows(
                TokenAlreadyRotatedException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals("TOKEN_ALREADY_ROTATED", ex.getErrorCode());
        verify(tokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("One nanosecond past 5-second grace period: reuse detected and family revoked with commit-before-throw")
    void rotateRefreshToken_oneNanoPastGrace_reuseDetected_revokesFamilyAndCommitsBeforeThrow() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";
        String successorHash = "successor-hash-abc";

        RefreshToken rotatedToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        // 5 seconds and 1 nanosecond ago: outside grace
        rotatedToken.setRevokedAt(now.minusSeconds(5).minusNanos(1));
        rotatedToken.setReplacedByTokenHash(successorHash);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(rotatedToken));

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.TOKEN_REUSE_DETECTED, ex.getReason());

        // Commit-before-throw verification: family revocation committed to DB before exception thrown
        InOrder inOrder = inOrder(tokenRepository, transactionManager);
        inOrder.verify(tokenRepository).revokeFamily(eq(family), eq(now));
        inOrder.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("Old rotated token with past expiry and elapsed grace period: reuse detected and family revoked with commit-before-throw")
    void rotateRefreshToken_expiredRotatedToken_outsideGrace_reuseDetected_revokesFamilyAndCommitsBeforeThrow() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";
        String successorHash = "successor-hash-abc";

        // Old refresh token that is already rotated and its expiry time is in the past
        RefreshToken expiredRotatedToken = new RefreshToken(user, tokenHash, family, now.minusSeconds(10));
        // Grace period has elapsed (rotated 8 days ago, grace period is 5 seconds)
        expiredRotatedToken.setRevokedAt(now.minus(Duration.ofDays(8)));
        expiredRotatedToken.setReplacedByTokenHash(successorHash);

        // Successor belongs to the same user and family, and is active
        RefreshToken successor = new RefreshToken(user, successorHash, family, now.plus(Duration.ofDays(7)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredRotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(expiredRotatedToken));
        when(tokenRepository.findByTokenHash(successorHash)).thenReturn(Optional.of(successor));
        // Family has an active token
        when(tokenRepository.hasActiveTokenInFamily(family, now)).thenReturn(true);

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.TOKEN_REUSE_DETECTED, ex.getReason());

        // Verify family revocation and commit-before-throw
        InOrder inOrder = inOrder(tokenRepository, transactionManager);
        inOrder.verify(tokenRepository).revokeFamily(eq(family), eq(now));
        inOrder.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("Within grace period but family has no active token: reuse detected and family revoked")
    void rotateRefreshToken_withinGrace_noActiveTokenInFamily_reuseDetected() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";
        String successorHash = "successor-hash-abc";

        RefreshToken rotatedToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        rotatedToken.setRevokedAt(now.minusSeconds(2));
        rotatedToken.setReplacedByTokenHash(successorHash);

        RefreshToken successor = new RefreshToken(user, successorHash, family, now.plus(Duration.ofDays(7)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHash(successorHash)).thenReturn(Optional.of(successor));
        // No active token in family
        when(tokenRepository.hasActiveTokenInFamily(family, now)).thenReturn(false);

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.TOKEN_REUSE_DETECTED, ex.getReason());
        verify(tokenRepository).revokeFamily(eq(family), eq(now));
    }

    @Test
    @DisplayName("Within grace period but successor does not exist: reuse detected")
    void rotateRefreshToken_invalidSuccessor_reuseDetected() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";
        String successorHash = "successor-hash-abc";

        RefreshToken rotatedToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        rotatedToken.setRevokedAt(now.minusSeconds(2));
        rotatedToken.setReplacedByTokenHash(successorHash);

        // Successor does not exist
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHash(successorHash)).thenReturn(Optional.empty());

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.TOKEN_REUSE_DETECTED, ex.getReason());
        verify(tokenRepository).revokeFamily(eq(family), eq(now));
    }

    @Test
    @DisplayName("Rotated token whose successor belongs to a different family: reuse detected and family revoked with commit-before-throw")
    void rotateRefreshToken_successorBelongsToDifferentFamily_reuseDetected_revokesFamilyAndCommitsBeforeThrow() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String originalFamily = "family-uuid-123";
        String differentFamily = "different-family-uuid-456";
        String successorHash = "successor-hash-abc";

        RefreshToken rotatedToken = new RefreshToken(user, tokenHash, originalFamily, now.plus(Duration.ofDays(7)));
        rotatedToken.setRevokedAt(now.minusSeconds(2));
        rotatedToken.setReplacedByTokenHash(successorHash);

        // Successor exists and belongs to the same user, but belongs to a DIFFERENT family
        RefreshToken successor = new RefreshToken(user, successorHash, differentFamily, now.plus(Duration.ofDays(7)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(rotatedToken));
        when(tokenRepository.findByTokenHash(successorHash)).thenReturn(Optional.of(successor));
        when(tokenRepository.hasActiveTokenInFamily(originalFamily, now)).thenReturn(true);

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.TOKEN_REUSE_DETECTED, ex.getReason());

        // Verify original token's family is revoked and transaction commits before throw
        InOrder inOrder = inOrder(tokenRepository, transactionManager);
        inOrder.verify(tokenRepository).revokeFamily(eq(originalFamily), eq(now));
        inOrder.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("Token revoked without successor throws InvalidRefreshTokenException")
    void rotateRefreshToken_revokedWithoutSuccessor_throwsInvalidRefreshTokenException() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";

        RefreshToken revokedToken = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        revokedToken.setRevokedAt(now.minusSeconds(10));
        // replacedByTokenHash is null

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));
        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(revokedToken));

        InvalidRefreshTokenException ex = assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertEquals(InvalidRefreshTokenException.Reason.INVALID_TOKEN, ex.getReason());
        verify(tokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("Logout revokes token family under User and Family advisory locks")
    void logout_revokesFamilyUnderLocks() {
        mockTransactionCommit();

        String rawToken = refreshTokenService.generateRawToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);
        String family = "family-uuid-123";

        RefreshToken token = new RefreshToken(user, tokenHash, family, now.plus(Duration.ofDays(7)));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.logout(rawToken);

        InOrder inOrder = inOrder(tokenRepository, transactionManager);
        inOrder.verify(tokenRepository).lockUserTokens(100L);
        inOrder.verify(tokenRepository).lockTokenFamily(family);
        inOrder.verify(tokenRepository).revokeFamily(eq(family), eq(now));
        inOrder.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("RevokeAllForUser revokes all tokens under user advisory lock")
    void revokeAllForUser_revokesAllUnderUserLock() {
        mockTransactionCommit();

        refreshTokenService.revokeAllForUser(100L);

        InOrder inOrder = inOrder(tokenRepository, transactionManager);
        inOrder.verify(tokenRepository).lockUserTokens(100L);
        inOrder.verify(tokenRepository).revokeAllByUserId(eq(100L), eq(now));
        inOrder.verify(transactionManager).commit(transactionStatus);
    }
}

