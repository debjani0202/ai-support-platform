package com.supportai.auth.service;

import com.supportai.auth.dto.ForgotPasswordRequest;
import com.supportai.auth.dto.ForgotPasswordResponse;
import com.supportai.auth.dto.ResetPasswordRequest;
import com.supportai.auth.dto.VerifyResetTokenResponse;
import com.supportai.auth.entity.PasswordResetToken;
import com.supportai.auth.repository.PasswordResetTokenRepository;
import com.supportai.auth.repository.RefreshTokenRepository;
import com.supportai.common.email.EmailService;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
            "If an account exists with this email address, a password reset link has been sent.";
    private static final long TOKEN_VALIDITY_MINUTES = 15;
    private static final long COOLDOWN_SECONDS = 60;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EntityManager entityManager;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            EntityManager entityManager,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.entityManager = entityManager;
        this.frontendUrl = frontendUrl;
    }

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            String frontendUrl
    ) {
        this(
                userRepository,
                tokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailService,
                null,
                frontendUrl
        );
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            return new ForgotPasswordResponse(GENERIC_FORGOT_PASSWORD_MESSAGE);
        }

        String email = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return new ForgotPasswordResponse(GENERIC_FORGOT_PASSWORD_MESSAGE);
        }

        User user = userOpt.get();

        // Lock user tokens before checking cooldown, invalidating tokens, or creating new tokens.
        // Serializes concurrent forgot-password requests for the same user.
        refreshTokenRepository.lockUserTokens(user.getId());

        // 60-second cooldown check to prevent spamming
        Optional<PasswordResetToken> latestTokenOpt =
                tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latestTokenOpt.isPresent() && latestTokenOpt.get().getCreatedAt() != null) {
            long secondsSinceLast = Duration.between(latestTokenOpt.get().getCreatedAt(), Instant.now()).getSeconds();
            if (secondsSinceLast < COOLDOWN_SECONDS) {
                return new ForgotPasswordResponse(GENERIC_FORGOT_PASSWORD_MESSAGE);
            }
        }

        // Invalidate any existing active tokens for this user
        tokenRepository.invalidateActiveTokensForUser(user.getId(), Instant.now());

        // Generate 32-byte secure random token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);

        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(TOKEN_VALIDITY_MINUTES));

        PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, expiresAt);
        tokenRepository.save(resetToken);

        String baseUrl = frontendUrl.replaceAll("/+$", "");
        String resetLink = baseUrl + "/reset-password?token=" + rawToken;

        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        return new ForgotPasswordResponse(GENERIC_FORGOT_PASSWORD_MESSAGE);
    }

    @Transactional(readOnly = true)
    public VerifyResetTokenResponse verifyResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return new VerifyResetTokenResponse(false, "Invalid or missing password reset token");
        }

        String tokenHash = hashToken(rawToken.trim());
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            return new VerifyResetTokenResponse(false, "Invalid or expired password reset token");
        }

        PasswordResetToken token = tokenOpt.get();

        if (token.isUsed()) {
            return new VerifyResetTokenResponse(false, "This password reset token has already been used");
        }

        if (token.isExpired()) {
            return new VerifyResetTokenResponse(false, "This password reset token has expired");
        }

        return new VerifyResetTokenResponse(true, "Token is valid");
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }

        String tokenHash = hashToken(request.token().trim());

        // 1. Unlocked lookup to discover the token's associated user ID
        PasswordResetToken initialToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        Long userId = initialToken.getUser().getId();

        // 2. Acquire user-level PostgreSQL advisory lock
        refreshTokenRepository.lockUserTokens(userId);

        // 3. Re-read reset token using pessimistic write lock while user advisory lock is held
        PasswordResetToken token = tokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        // 4. Synchronize entity state with committed database state to prevent JPA L1 cache staleness
        if (entityManager != null) {
            entityManager.refresh(token);
            if (token.getUser() != null) {
                entityManager.refresh(token.getUser());
            }
        }

        // 5. Revalidate token state after acquiring the lock and refreshing
        if (token.isUsed()) {
            throw new IllegalArgumentException("This password reset token has already been used");
        }

        if (token.isExpired()) {
            throw new IllegalArgumentException("This password reset token has expired");
        }

        if (!token.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }

        User user = token.getUser();

        // 6. Validate that the new password differs from the fresh current password
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        // 7. Update user password
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Explicitly flush pending user updates to PostgreSQL before bulk revocation
        // (because @Modifying(clearAutomatically = true) clears the persistence context)
        if (entityManager != null) {
            entityManager.flush();
        }

        // 8. Revoke all active refresh tokens for the user
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());

        // 9. Mark the reset token as used and persist
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        if (entityManager != null) {
            entityManager.flush();
        }
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
}
