package com.supportai.ticket.dto;

import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void createTicketRequest_withValidDescription_hasNoViolations() {
        String validDesc = "A".repeat(10000);
        CreateTicketRequest request = new CreateTicketRequest(
                "Valid Title",
                validDesc,
                TicketCategory.TECHNICAL_ISSUE,
                TicketPriority.MEDIUM
        );

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid description up to 10,000 characters must be accepted");
    }

    @Test
    void createTicketRequest_withDescriptionExceeding10000Chars_isRejected() {
        String tooLongDesc = "A".repeat(10001);
        CreateTicketRequest request = new CreateTicketRequest(
                "Valid Title",
                tooLongDesc,
                TicketCategory.TECHNICAL_ISSUE,
                TicketPriority.MEDIUM
        );

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Description exceeding 10,000 chars must produce a violation");

        ConstraintViolation<CreateTicketRequest> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertEquals("Description must not exceed 10000 characters", violation.getMessage());
    }

    @Test
    void createTicketRequest_withBlankDescription_isRejected() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Valid Title",
                "   ",
                TicketCategory.TECHNICAL_ISSUE,
                TicketPriority.MEDIUM
        );

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void updateTicketRequest_withValidDescription_hasNoViolations() {
        String validDesc = "B".repeat(10000);
        UpdateTicketRequest request = new UpdateTicketRequest(
                "Valid Title",
                validDesc,
                "IN_PROGRESS",
                TicketCategory.BUG_REPORT,
                TicketPriority.HIGH
        );

        Set<ConstraintViolation<UpdateTicketRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid description up to 10,000 characters must be accepted");
    }

    @Test
    void updateTicketRequest_withDescriptionExceeding10000Chars_isRejected() {
        String tooLongDesc = "B".repeat(10001);
        UpdateTicketRequest request = new UpdateTicketRequest(
                "Valid Title",
                tooLongDesc,
                "IN_PROGRESS",
                TicketCategory.BUG_REPORT,
                TicketPriority.HIGH
        );

        Set<ConstraintViolation<UpdateTicketRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size(), "Description exceeding 10,000 chars must produce a violation");

        ConstraintViolation<UpdateTicketRequest> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertEquals("Description must not exceed 10000 characters", violation.getMessage());
    }

    @Test
    void updateTicketRequest_withBlankDescription_isRejected() {
        UpdateTicketRequest request = new UpdateTicketRequest(
                "Valid Title",
                "",
                "OPEN",
                TicketCategory.PAYMENT,
                TicketPriority.LOW
        );

        Set<ConstraintViolation<UpdateTicketRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }
}
