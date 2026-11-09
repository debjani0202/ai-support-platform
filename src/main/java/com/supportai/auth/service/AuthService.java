
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;
        private final RefreshTokenRepository refreshTokenRepository;
        private final EntityManager entityManager;

        public AuthService(
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        RefreshTokenService refreshTokenService,
                        RefreshTokenRepository refreshTokenRepository,
                        EntityManager entityManager) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.refreshTokenService = refreshTokenService;
                this.refreshTokenRepository = refreshTokenRepository;
                this.entityManager = entityManager;
        }

        @Transactional
        public AuthResponse register(RegisterRequest request) {

                String email = request.email()
                                .trim()
                                .toLowerCase();

                if (userRepository.existsByEmail(email)) {
                        throw new IllegalArgumentException(
                                        "Email is already registered");
                }

                String encodedPassword = passwordEncoder.encode(
                                request.password());

                // Public registration always creates a CUSTOMER.
                User user = new User(
                                request.name().trim(),
                                email,
                                encodedPassword,
                                Role.CUSTOMER);

                User savedUser = userRepository.save(user);

                return toResponse(savedUser);
        }

        @Transactional
        public AuthResult login(LoginRequest request) {

                String email = request.email()
                                .trim()
                                .toLowerCase();

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Invalid email or password"));

                // Step 1: Check the password before acquiring the database lock.
                String initialPasswordHash = user.getPassword();

                if (!passwordEncoder.matches(
                                request.password(),
                                initialPasswordHash)) {
                        throw new IllegalArgumentException(
                                        "Invalid email or password");
                }

                // Step 2: Acquire the user-level advisory lock.
                refreshTokenRepository.lockUserTokens(user.getId());

                // Step 3: Reload the user to obtain the latest committed password hash.
                entityManager.refresh(user);

                // Step 4: Verify that the password was not changed concurrently.
                if (!user.getPassword().equals(initialPasswordHash)) {
                        throw new IllegalArgumentException(
                                        "Invalid email or password");
                }

                // Step 5: Create the refresh token and access token.
                return refreshTokenService.createRefreshToken(user);
        }

        /**
         * Changes the password of the specified user.
         * The caller must obtain userId from the authenticated
         * security context in the controller.
         */
        @Transactional
        public void changePassword(
                        Long userId,
                        ChangePasswordRequest request) {

                // Acquire the user lock before updating the password or revoking tokens.
                refreshTokenRepository.lockUserTokens(userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "User account not found"));

                if (!passwordEncoder.matches(
                                request.currentPassword(),
                                user.getPassword())) {
                        throw new IllegalArgumentException(
                                        "Current password is incorrect");
                }

                if (passwordEncoder.matches(
                                request.newPassword(),
                                user.getPassword())) {
                        throw new IllegalArgumentException(
                                        "New password must be different");
                }

                String encodedPassword = passwordEncoder.encode(
                                request.newPassword());

                user.setPassword(encodedPassword);

                // Revoke all active refresh tokens for this user.
                refreshTokenRepository.revokeAllByUserId(
                                userId,
                                Instant.now());
        }

        @Transactional(readOnly = true)
        public LoginResponse refreshToken(Long currentUserId) {

                User user = userRepository.findById(currentUserId)
                                .orElseThrow(() -> new com.supportai.exception.ResourceNotFoundException(
                                                "User not found with id: " + currentUserId));

                String accessToken = jwtService.generateToken(user);

                return new LoginResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getRole(),
                                accessToken,
                                "Bearer");
        }

        private AuthResponse toResponse(User user) {

                return new AuthResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getRole());
        }
}
