
package com.supportai.auth.service;

import com.supportai.auth.dto.AuthResponse;
import com.supportai.auth.dto.LoginRequest;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.dto.RegisterRequest;
import com.supportai.security.jwt.JwtService;
import com.supportai.user.entity.Role; // Provides CUSTOMER, AGENT, and ADMIN roles.
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service tells Spring that this class contains business logic.
@Service
public class AuthService {

    // Repository communicates with the database to find and save users.
    private final UserRepository userRepository;

    // PasswordEncoder hashes passwords before they are stored.
    private final PasswordEncoder passwordEncoder;

    // JwtService generates the login token after successful authentication.
    private final JwtService jwtService;

    // Constructor injection: Spring provides these required dependencies.
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Spring manages the database transaction for registration.
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Remove surrounding spaces and normalize email case.
        String email = request.email()
                .trim()
                .toLowerCase();

        // Prevent registration when the email already exists.
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email is already registered"
            );
        }

        // Never store the user's raw password in the database.
        String encodedPassword = passwordEncoder.encode(
                request.password()
        );

        // SECURITY: The server assigns CUSTOMER.
        // Never trust the client to choose ADMIN or AGENT.
        User user = new User(
                request.name().trim(),
                email,
                encodedPassword,
                Role.CUSTOMER
        );

        // Save the new user and receive the saved entity.
        User savedUser = userRepository.save(user);

        // Return safe user details without the password hash.
        return toResponse(savedUser);
    }

    // This method only reads data, so the transaction is read-only.
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        // Normalize the email the same way as during registration.
        String email = request.email()
                .trim()
                .toLowerCase();

        // Find the account by email.
        // Use the same error message for an unknown email and a wrong password.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid email or password"
                        )
                );

        // Compare the submitted password with the stored password hash.
        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Invalid email or password"
            );
        }

        // Generate a JWT after the credentials are verified.
        String accessToken = jwtService.generateToken(user);

        // Return the logged-in user's details and access token.
        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                accessToken,
                "Bearer"
        );
    }

    // Converts the User entity into a response DTO.
    // This prevents internal entity details, such as the password hash,
    // from being included in the registration response.
    private AuthResponse toResponse(User user) {

        return new AuthResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
