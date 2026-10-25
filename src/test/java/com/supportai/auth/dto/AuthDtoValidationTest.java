package com.supportai.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void registerRequest_with72CharPassword_hasNoViolations() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "jane@example.com",
                "A".repeat(72)
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Password with 72 characters must be valid");
    }

    @Test
    void registerRequest_with73CharPassword_isRejected() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "jane@example.com",
                "A".repeat(73)
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Password exceeding 72 characters must be rejected");

        ConstraintViolation<RegisterRequest> violation = violations.iterator().next();
        assertEquals("password", violation.getPropertyPath().toString());
        assertEquals("Password must be between 8 and 72 characters", violation.getMessage());
    }

    @Test
    void registerRequest_with7CharPassword_isRejected() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "jane@example.com",
                "A".repeat(7)
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Password shorter than 8 characters must be rejected");

        ConstraintViolation<RegisterRequest> violation = violations.iterator().next();
        assertEquals("password", violation.getPropertyPath().toString());
        assertEquals("Password must be between 8 and 72 characters", violation.getMessage());
    }

    @Test
    void registerRequest_with8CharPassword_hasNoViolations() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "jane@example.com",
                "A".repeat(8)
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Password with 8 characters must be valid");
    }

    @Test
    void changePasswordRequest_with72CharNewPassword_hasNoViolations() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPassword123",
                "B".repeat(72)
        );

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "New password with 72 characters must be valid");
    }

    @Test
    void changePasswordRequest_with73CharNewPassword_isRejected() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPassword123",
                "B".repeat(73)
        );

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "New password exceeding 72 characters must be rejected");

        ConstraintViolation<ChangePasswordRequest> violation = violations.iterator().next();
        assertEquals("newPassword", violation.getPropertyPath().toString());
        assertEquals("New password must be between 8 and 72 characters", violation.getMessage());
    }

    @Test
    void changePasswordRequest_with7CharNewPassword_isRejected() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPassword123",
                "B".repeat(7)
        );

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "New password shorter than 8 characters must be rejected");

        ConstraintViolation<ChangePasswordRequest> violation = violations.iterator().next();
        assertEquals("newPassword", violation.getPropertyPath().toString());
        assertEquals("New password must be between 8 and 72 characters", violation.getMessage());
    }

    @Test
    void changePasswordRequest_with8CharNewPassword_hasNoViolations() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPassword123",
                "B".repeat(8)
        );

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "New password with 8 characters must be valid");
    }
}
