package com.supportai.user.cli;

import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminProvisioningRunnerTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AdminProvisioningRunner runner;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        runner = new AdminProvisioningRunner(userRepository, passwordEncoder);
    }

    @Test
    void executeProvisioning_successfullyCreatesAdminWithEncodedPassword() {
        when(userRepository.findByEmail("admin@supportai.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ComplexAdminPass123!")).thenReturn("encoded_admin_hash");

        int exitCode = runner.executeProvisioning("admin@supportai.com", "ComplexAdminPass123!", "System Admin");

        assertEquals(0, exitCode);
        assertEquals(0, runner.getExitCode());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("System Admin", savedUser.getName());
        assertEquals("admin@supportai.com", savedUser.getEmail());
        assertEquals("encoded_admin_hash", savedUser.getPassword());
        assertEquals(Role.ADMIN, savedUser.getRole());
    }

    @Test
    void executeProvisioning_defaultsNameToSystemAdministratorWhenBlank() {
        when(userRepository.findByEmail("admin@supportai.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_admin_hash");

        int exitCode = runner.executeProvisioning("admin@supportai.com", "ComplexAdminPass123!", "   ");

        assertEquals(0, exitCode);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("System Administrator", userCaptor.getValue().getName());
    }

    @Test
    void executeProvisioning_rejectsMissingOrBlankEmail() {
        assertEquals(1, runner.executeProvisioning(null, "ComplexAdminPass123!", "Admin"));
        assertEquals(1, runner.executeProvisioning("   ", "ComplexAdminPass123!", "Admin"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_rejectsInvalidEmailFormat() {
        assertEquals(1, runner.executeProvisioning("not-an-email", "ComplexAdminPass123!", "Admin"));
        assertEquals(1, runner.executeProvisioning("admin@", "ComplexAdminPass123!", "Admin"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_rejectsMissingOrWeakPassword() {
        assertEquals(1, runner.executeProvisioning("admin@supportai.com", null, "Admin"));
        assertEquals(1, runner.executeProvisioning("admin@supportai.com", "short", "Admin"));
        assertEquals(1, runner.executeProvisioning("admin@supportai.com", "a".repeat(73), "Admin"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_rejectsExcessivelyLongName() {
        assertEquals(1, runner.executeProvisioning("admin@supportai.com", "ComplexAdminPass123!", "A".repeat(101)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_rejectsExistingCustomerWithoutModifyingAccount() {
        User customer = mock(User.class);
        when(customer.getRole()).thenReturn(Role.CUSTOMER);
        when(userRepository.findByEmail("customer@supportai.com")).thenReturn(Optional.of(customer));

        int exitCode = runner.executeProvisioning("customer@supportai.com", "ComplexAdminPass123!", "Admin");

        assertEquals(1, exitCode);
        verify(customer, never()).setRole(any());
        verify(customer, never()).setPassword(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_rejectsExistingAgentWithoutModifyingAccount() {
        User agent = mock(User.class);
        when(agent.getRole()).thenReturn(Role.AGENT);
        when(userRepository.findByEmail("agent@supportai.com")).thenReturn(Optional.of(agent));

        int exitCode = runner.executeProvisioning("agent@supportai.com", "ComplexAdminPass123!", "Admin");

        assertEquals(1, exitCode);
        verify(agent, never()).setRole(any());
        verify(agent, never()).setPassword(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_idempotentWhenAdminAlreadyExists() {
        User existingAdmin = mock(User.class);
        when(existingAdmin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findByEmail("admin@supportai.com")).thenReturn(Optional.of(existingAdmin));

        int exitCode = runner.executeProvisioning("admin@supportai.com", "NewPasswordThatShouldNotBeApplied123!", "Admin");

        assertEquals(0, exitCode);
        verify(existingAdmin, never()).setPassword(any());
        verify(existingAdmin, never()).setRole(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void executeProvisioning_handlesDataIntegrityViolationWhenAdminConcurrentlyCreated() {
        when(userRepository.findByEmail("admin@supportai.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new User("Admin", "admin@supportai.com", "hash", Role.ADMIN)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        int exitCode = runner.executeProvisioning("admin@supportai.com", "ComplexAdminPass123!", "Admin");

        assertEquals(0, exitCode);
    }

    @Test
    void run_rejectsPasswordInCommandLineArguments() {
        runner.run("--app.admin.provision=true", "--ADMIN_PASSWORD=secret");
        assertEquals(1, runner.getExitCode());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void run_failsWhenEnvironmentCredentialsAreMissing() {
        runner.run("--app.admin.provision=true");
        assertEquals(1, runner.getExitCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void run_ignoresJvmSystemPropertiesAndFailsWhenEnvVarsAreMissing() {
        System.setProperty("ADMIN_EMAIL", "sysprop@supportai.com");
        System.setProperty("ADMIN_PASSWORD", "SysPropPassword123!");
        System.setProperty("ADMIN_NAME", "SysProp Admin");
        try {
            runner.run("--app.admin.provision=true");
            assertEquals(1, runner.getExitCode());
            verify(userRepository, never()).save(any());
        } finally {
            System.clearProperty("ADMIN_EMAIL");
            System.clearProperty("ADMIN_PASSWORD");
            System.clearProperty("ADMIN_NAME");
        }
    }

    @Test
    void executeProvisioning_preservesExactPasswordWithoutTrimming() {
        when(userRepository.findByEmail("admin@supportai.com")).thenReturn(Optional.empty());
        String exactPasswordWithSpaces = "  PassWithSpaces123!  ";
        when(passwordEncoder.encode(exactPasswordWithSpaces)).thenReturn("encoded_exact_hash");

        int exitCode = runner.executeProvisioning("admin@supportai.com", exactPasswordWithSpaces, "System Admin");

        assertEquals(0, exitCode);
        verify(passwordEncoder).encode(exactPasswordWithSpaces);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("encoded_exact_hash", userCaptor.getValue().getPassword());
    }

    @Test
    void executeProvisioning_rejectsBlankPasswordEvenIfLengthIsSufficient() {
        assertEquals(1, runner.executeProvisioning("admin@supportai.com", "          ", "Admin"));
        verify(userRepository, never()).save(any());
    }
}
