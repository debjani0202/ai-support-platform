
package com.supportai.auth.service;

import com.supportai.auth.dto.AuthResponse;
import com.supportai.auth.dto.ChangePasswordRequest;
import com.supportai.auth.dto.LoginRequest;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.dto.RegisterRequest;
import com.supportai.security.jwt.JwtService;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email is already registered"
            );
        }

        String encodedPassword = passwordEncoder.encode(
                request.password()
        );

        // Public registration always creates a CUSTOMER.
        User user = new User(
                request.name().trim(),
                email,
                encodedPassword,
                Role.CUSTOMER
        );

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Invalid email or password"
            );
        }

        String accessToken = jwtService.generateToken(user);

        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                accessToken,
                "Bearer"
        );
    }

    /**
     * Changes the password of the specified user.
     * The caller must obtain userId from the authenticated
     * security context in the controller.
     */
    @Transactional
    public void changePassword(
            Long userId,
            ChangePasswordRequest request
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User account not found"
                        )
                );

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Current password is incorrect"
            );
        }

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "New password must be different"
            );
        }

        String encodedPassword = passwordEncoder.encode(
                request.newPassword()
        );

        user.setPassword(encodedPassword);
    }

    private AuthResponse toResponse(User user) {

        return new AuthResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
