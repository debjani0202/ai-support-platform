package com.supportai.auth.service;

import com.supportai.auth.dto.AuthResult;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.entity.RefreshToken;
import com.supportai.auth.repository.RefreshTokenRepository;
import com.supportai.exception.InvalidRefreshTokenException;
import com.supportai.exception.TokenAlreadyRotatedException;
import com.supportai.security.jwt.JwtService;
import com.supportai.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    public static final Duration GRACE_PERIOD = Duration.ofSeconds(5);

    private final RefreshTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final TransactionTemplate transactionTemplate;
    private final long refreshTokenExpirationDays;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public RefreshTokenService(
            RefreshTokenRepository tokenRepository,
            JwtService jwtService,
            PlatformTransactionManager transactionManager,
            @Value("${app.auth.refresh-token.expiration-days:7}") long refreshTokenExpirationDays
    ) {
        this(tokenRepository, jwtService, transactionManager, refreshTokenExpirationDays, Clock.systemUTC());
    }

    public RefreshTokenService(
            RefreshTokenRepository tokenRepository,
            JwtService jwtService,
            PlatformTransactionManager transactionManager,
            long refreshTokenExpirationDays,
            Clock clock
    ) {
        if (refreshTokenExpirationDays <= 0) {
            throw new IllegalArgumentException(
                    "Refresh token expiration days must be greater than zero, got: " + refreshTokenExpirationDays
            );
        }
        this.tokenRepository = tokenRepository;
        this.jwtService = jwtService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
        this.clock = clock;
    }

    /**
     * Issues an initial refresh token for a newly authenticated session.
     * Generates a new family UUID and creates the initial active token.
     */
    public AuthResult createRefreshToken(User user) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        String family = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(Duration.ofDays(refreshTokenExpirationDays));

        RefreshToken token = new RefreshToken(user, tokenHash, family, expiresAt);
        tokenRepository.save(token);

        String accessToken = jwtService.generateToken(user);
        LoginResponse loginResponse = new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                accessToken,
                "Bearer"
        );

        return new AuthResult(loginResponse, rawToken);
    }

    /**
     * Rotates a refresh token following the strict lock hierarchy:
     * 1. User advisory lock
     * 2. Family advisory lock
     * 3. Row pessimistic lock
     *
     * In the case of reuse detection, family revocation is committed before
     * throwing the domain exception.
     */
    public AuthResult rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException(
                    InvalidRefreshTokenException.Reason.INVALID_TOKEN,
                    "Refresh token is required"
            );
        }

        String incomingTokenHash = hashToken(rawRefreshToken.trim());

        RotationOutcome outcome = transactionTemplate.execute(status -> {
            // Tier 0: Unlocked discovery of family and user ID
            Optional<RefreshToken> initialOpt = tokenRepository.findByTokenHash(incomingTokenHash);
            if (initialOpt.isEmpty()) {
                return RotationOutcome.invalid("Invalid refresh token");
            }

            RefreshToken initialToken = initialOpt.get();
            Long userId = initialToken.getUser().getId();
            String family = initialToken.getTokenFamily();

            // Lock hierarchy: User -> Family -> Row
            tokenRepository.lockUserTokens(userId);
            tokenRepository.lockTokenFamily(family);

            Optional<RefreshToken> lockedOpt = tokenRepository.findByTokenHashWithLock(incomingTokenHash);
            if (lockedOpt.isEmpty()) {
                return RotationOutcome.invalid("Invalid refresh token");
            }

            RefreshToken token = lockedOpt.get();
            Instant now = Instant.now(clock);

            // Check 1: Rotated token (concurrent grace period vs reuse)
            // Rotated tokens must always be evaluated for reuse detection regardless of token expiration
            if (token.isRotated()) {
                boolean hasSuccessor = token.getReplacedByTokenHash() != null;
                Optional<RefreshToken> successorOpt = hasSuccessor
                        ? tokenRepository.findByTokenHash(token.getReplacedByTokenHash())
                        : Optional.empty();

                boolean successorValid = successorOpt.isPresent()
                        && successorOpt.get().getUser().getId().equals(userId)
                        && successorOpt.get().getTokenFamily().equals(family);

                Instant graceDeadline = token.getRevokedAt().plus(GRACE_PERIOD);
                boolean withinGrace = !now.isAfter(graceDeadline);
                boolean familyHasActive = tokenRepository.hasActiveTokenInFamily(family, now);

                if (successorValid && withinGrace && familyHasActive) {
                    return RotationOutcome.alreadyRotated("Token was rotated by a concurrent request");
                }

                // Outside grace window or invalid successor -> Reuse detected! Revoke entire family!
                tokenRepository.revokeFamily(family, now);
                return RotationOutcome.reuseDetected("Refresh token reuse detected: entire token family revoked");
            }

            // Check 2: Expiration (for non-rotated tokens)
            if (token.isExpired(now)) {
                return RotationOutcome.expired("Refresh token has expired");
            }

            // Check 3: Revoked without successor (e.g. manual revocation / logout)
            if (token.isRevoked()) {
                return RotationOutcome.invalid("Refresh token has been revoked");
            }

            // Valid active token: perform normal rotation
            String newRawToken = generateRawToken();
            String newTokenHash = hashToken(newRawToken);

            token.setRevokedAt(now);
            token.setReplacedByTokenHash(newTokenHash);
            tokenRepository.save(token);

            Instant newExpiresAt = now.plus(Duration.ofDays(refreshTokenExpirationDays));
            RefreshToken successor = new RefreshToken(token.getUser(), newTokenHash, family, newExpiresAt);
            tokenRepository.save(successor);

            User user = token.getUser();
            String newAccessToken = jwtService.generateToken(user);
            LoginResponse loginResponse = new LoginResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole(),
                    newAccessToken,
                    "Bearer"
            );

            return RotationOutcome.success(new AuthResult(loginResponse, newRawToken));
        });

        if (outcome == null) {
            throw new InvalidRefreshTokenException(
                    InvalidRefreshTokenException.Reason.INVALID_TOKEN,
                    "Unable to rotate refresh token"
            );
        }

        return switch (outcome.type()) {
            case SUCCESS -> outcome.authResult();
            case ALREADY_ROTATED -> throw new TokenAlreadyRotatedException(outcome.message());
            case EXPIRED -> throw new InvalidRefreshTokenException(
                    InvalidRefreshTokenException.Reason.TOKEN_EXPIRED,
                    outcome.message()
            );
            case REUSE_DETECTED -> throw new InvalidRefreshTokenException(
                    InvalidRefreshTokenException.Reason.TOKEN_REUSE_DETECTED,
                    outcome.message()
            );
            case INVALID -> throw new InvalidRefreshTokenException(
                    InvalidRefreshTokenException.Reason.INVALID_TOKEN,
                    outcome.message()
            );
        };
    }

    /**
     * Revokes the token family associated with the provided raw token upon explicit logout.
     */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken.trim());

        transactionTemplate.executeWithoutResult(status -> {
            Optional<RefreshToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);
            if (tokenOpt.isEmpty()) {
                return;
            }

            RefreshToken token = tokenOpt.get();
            Long userId = token.getUser().getId();
            String family = token.getTokenFamily();

            tokenRepository.lockUserTokens(userId);
            tokenRepository.lockTokenFamily(family);

            tokenRepository.revokeFamily(family, Instant.now(clock));
        });
    }

    /**
     * Revokes all active refresh tokens for the specified user ID under user advisory lock.
     */
    public void revokeAllForUser(Long userId) {
        if (userId == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            tokenRepository.lockUserTokens(userId);
            tokenRepository.revokeAllByUserId(userId, Instant.now(clock));
        });
    }

    public String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private record RotationOutcome(
            OutcomeType type,
            AuthResult authResult,
            String message
    ) {
        enum OutcomeType {
            SUCCESS,
            ALREADY_ROTATED,
            EXPIRED,
            REUSE_DETECTED,
            INVALID
        }

        static RotationOutcome success(AuthResult authResult) {
            return new RotationOutcome(OutcomeType.SUCCESS, authResult, null);
        }

        static RotationOutcome alreadyRotated(String message) {
            return new RotationOutcome(OutcomeType.ALREADY_ROTATED, null, message);
        }

        static RotationOutcome expired(String message) {
            return new RotationOutcome(OutcomeType.EXPIRED, null, message);
        }

        static RotationOutcome reuseDetected(String message) {
            return new RotationOutcome(OutcomeType.REUSE_DETECTED, null, message);
        }

        static RotationOutcome invalid(String message) {
            return new RotationOutcome(OutcomeType.INVALID, null, message);
        }
    }
}

