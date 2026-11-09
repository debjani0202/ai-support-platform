package com.supportai.auth.entity;

import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenTest {

    private User user;
    private Instant expiryInstant;
    private RefreshToken token;

    @BeforeEach
    void setUp() {
        user = new User("Alice", "alice@example.com", "encoded-pwd", Role.CUSTOMER);
        expiryInstant = Instant.parse("2026-11-08T12:00:00.000000000Z");
        token = new RefreshToken(user, "dummy-hash", "family-uuid", expiryInstant);
    }

    @Test
    @DisplayName("One nanosecond before expiry: active and not expired")
    void oneNanosecondBeforeExpiry_isActive() {
        Instant oneNanoBefore = expiryInstant.minusNanos(1);

        assertFalse(token.isExpired(oneNanoBefore));
        assertTrue(token.isActive(oneNanoBefore));
    }

    @Test
    @DisplayName("Exactly at expiry instant: expired and inactive")
    void exactlyAtExpiry_isExpiredAndInactive() {
        Instant exactExpiry = expiryInstant;

        assertTrue(token.isExpired(exactExpiry));
        assertFalse(token.isActive(exactExpiry));
    }

    @Test
    @DisplayName("One nanosecond after expiry: expired and inactive")
    void oneNanosecondAfterExpiry_isExpiredAndInactive() {
        Instant oneNanoAfter = expiryInstant.plusNanos(1);

        assertTrue(token.isExpired(oneNanoAfter));
        assertFalse(token.isActive(oneNanoAfter));
    }

    @Test
    @DisplayName("Revoked token: inactive even before expiry")
    void revokedToken_isInactive() {
        Instant oneNanoBefore = expiryInstant.minusNanos(1);
        token.setRevokedAt(oneNanoBefore.minusSeconds(10));

        assertTrue(token.isRevoked());
        assertFalse(token.isActive(oneNanoBefore));
    }

    @Test
    @DisplayName("Rotated token: isRevoked and isRotated are true, and inactive")
    void rotatedToken_isRotatedAndInactive() {
        Instant oneNanoBefore = expiryInstant.minusNanos(1);
        token.setRevokedAt(oneNanoBefore);
        token.setReplacedByTokenHash("successor-hash");

        assertTrue(token.isRevoked());
        assertTrue(token.isRotated());
        assertFalse(token.isActive(oneNanoBefore));
    }
}

