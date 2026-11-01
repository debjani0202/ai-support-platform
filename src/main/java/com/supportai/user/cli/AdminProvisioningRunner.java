package com.supportai.user.cli;

import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.admin.provision", havingValue = "true")
public class AdminProvisioningRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioningRunner.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private int exitCode = 0;

    public AdminProvisioningRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Enforce that passwords are NEVER accepted via CLI arguments
        if (args != null) {
            for (String arg : args) {
                if (arg != null && (arg.toLowerCase().contains("password") || arg.contains("ADMIN_PASSWORD"))) {
                    log.error("Passing credentials via command-line arguments is strictly prohibited. Provide credentials via environment variables.");
                    this.exitCode = 1;
                    return;
                }
            }
        }

        String email = getEnvVariable("ADMIN_EMAIL");
        String password = System.getenv("ADMIN_PASSWORD");
        String name = getEnvVariable("ADMIN_NAME");

        this.exitCode = executeProvisioning(email, password, name);
    }

    public int executeProvisioning(String email, String password, String name) {
        if (email == null || email.isBlank()) {
            log.error("Admin provisioning failed: ADMIN_EMAIL is required and cannot be blank.");
            return 1;
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (normalizedEmail.length() > 255 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            log.error("Admin provisioning failed: ADMIN_EMAIL must be a valid email address and not exceed 255 characters.");
            return 1;
        }

        if (password == null || password.isBlank()) {
            log.error("Admin provisioning failed: ADMIN_PASSWORD is required and cannot be blank.");
            return 1;
        }

        if (password.length() < 8 || password.length() > 72) {
            log.error("Admin provisioning failed: ADMIN_PASSWORD must be between 8 and 72 characters.");
            return 1;
        }

        String adminName = (name != null && !name.isBlank()) ? name.trim() : "System Administrator";
        if (adminName.length() > 100) {
            log.error("Admin provisioning failed: ADMIN_NAME must not exceed 100 characters.");
            return 1;
        }

        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.getRole() == Role.ADMIN) {
                log.info("Admin account already exists for email '{}'. Account details and credentials remain unchanged.", normalizedEmail);
                return 0;
            } else {
                log.error("Account already exists for email '{}' with role {}. Automatic promotion of existing non-admin accounts is prohibited.",
                        normalizedEmail, existingUser.getRole());
                return 1;
            }
        }

        try {
            String encodedPassword = passwordEncoder.encode(password);
            User admin = new User(adminName, normalizedEmail, encodedPassword, Role.ADMIN);
            userRepository.save(admin);
            log.info("Initial admin account successfully provisioned for email: '{}'.", normalizedEmail);
            return 0;
        } catch (DataIntegrityViolationException ex) {
            // Concurrent execution race safety check
            Optional<User> recheck = userRepository.findByEmail(normalizedEmail);
            if (recheck.isPresent() && recheck.get().getRole() == Role.ADMIN) {
                log.info("Admin account was concurrently provisioned for email '{}'.", normalizedEmail);
                return 0;
            }
            log.error("Data integrity violation occurred while provisioning admin account for email '{}'.", normalizedEmail);
            return 1;
        } catch (Exception ex) {
            log.error("Unexpected error occurred while provisioning admin account for email '{}'.", normalizedEmail);
            return 1;
        }
    }

    private String getEnvVariable(String key) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return null;
    }

    @Override
    public int getExitCode() {
        return this.exitCode;
    }
}
