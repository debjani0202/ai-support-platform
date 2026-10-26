package com.supportai.auth.service;

import com.supportai.auth.dto.ForgotPasswordRequest;
import com.supportai.auth.dto.ForgotPasswordResponse;
import com.supportai.auth.dto.ResetPasswordRequest;
import com.supportai.auth.dto.VerifyResetTokenResponse;
import com.supportai.auth.entity.PasswordResetToken;
import com.supportai.auth.repository.PasswordResetTokenRepository;
import com.supportai.common.email.EmailService;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
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
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("This password reset token has already been used");
        }

        if (token.isExpired()) {
            throw new IllegalArgumentException("This password reset token has expired");
        }

        User user = token.getUser();

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
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
