package com.supportai.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleGenericException_returnsHttp500_withSafeMessage() {
        String sensitiveInternalMessage = "PSQLException: password authentication failed for user 'postgres' at jdbc:5434";
        Exception unhandledException = new RuntimeException(sensitiveInternalMessage);

        ResponseEntity<Map<String, Object>> response =
                exceptionHandler.handleGenericException(unhandledException);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertEquals(500, body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        assertEquals("An unexpected error occurred. Please try again later.", body.get("message"));
        assertNotNull(body.get("timestamp"));

        // Verify that sensitive internal exception details are NEVER exposed in the response
        assertFalse(body.get("message").toString().contains(sensitiveInternalMessage));
        assertFalse(body.toString().contains("PSQLException"));
    }

    @Test
    void handleResourceNotFound_preserves404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Ticket not found with ID: 42");

        ResponseEntity<Map<String, Object>> response =
                exceptionHandler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Not Found", response.getBody().get("error"));
        assertEquals("Ticket not found with ID: 42", response.getBody().get("message"));
    }

    @Test
    void handleForbidden_preserves403() {
        ForbiddenException ex = new ForbiddenException("Customers cannot change ticket status");

        ResponseEntity<Map<String, Object>> response =
                exceptionHandler.handleForbidden(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().get("status"));
        assertEquals("Forbidden", response.getBody().get("error"));
        assertEquals("Customers cannot change ticket status", response.getBody().get("message"));
    }

    @Test
    void handleIllegalArgument_preserves400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid sort field");

        ResponseEntity<Map<String, Object>> response =
                exceptionHandler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Invalid sort field", response.getBody().get("message"));
    }
}
