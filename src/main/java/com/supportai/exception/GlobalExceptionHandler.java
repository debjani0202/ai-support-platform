
package com.supportai.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        private final String cookieName;
        private final boolean cookieSecure;
        private final String cookieSameSite;

        public GlobalExceptionHandler(
                        @Value("${app.auth.cookie.name:refreshToken}") String cookieName,
                        @Value("${app.auth.cookie.secure:false}") boolean cookieSecure,
                        @Value("${app.auth.cookie.same-site:Lax}") String cookieSameSite) {
                this.cookieName = cookieName;
                this.cookieSecure = cookieSecure;
                this.cookieSameSite = cookieSameSite;
        }

        @ExceptionHandler(InvalidRefreshTokenException.class)
        public ResponseEntity<Map<String, Object>> handleInvalidRefreshToken(
                        InvalidRefreshTokenException ex) {

                Map<String, Object> error = new LinkedHashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.UNAUTHORIZED.value());
                error.put("error", "Unauthorized");
                error.put("message", ex.getMessage());
                error.put("reason", ex.getReason().name());

                ResponseCookie clearCookie = ResponseCookie.from(cookieName, "")
                                .httpOnly(true)
                                .secure(cookieSecure)
                                .sameSite(cookieSameSite)
                                .path("/api/auth")
                                .maxAge(0)
                                .build();

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                                .body(error);
        }

        @ExceptionHandler(TokenAlreadyRotatedException.class)
        public ResponseEntity<Map<String, Object>> handleTokenAlreadyRotated(
                        TokenAlreadyRotatedException ex) {

                Map<String, Object> error = new LinkedHashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.CONFLICT.value());
                error.put("error", "Conflict");
                error.put("message", ex.getMessage());
                error.put("errorCode", ex.getErrorCode());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(error);
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<Map<String, Object>> handleResourceNotFound(
                        ResourceNotFoundException ex) {

                Map<String, Object> error = new LinkedHashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.NOT_FOUND.value());
                error.put("error", "Not Found");
                error.put("message", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(error);
        }

        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<Map<String, Object>> handleForbidden(
                        ForbiddenException ex) {

                Map<String, Object> error = new LinkedHashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.FORBIDDEN.value());
                error.put("error", "Forbidden");
                error.put("message", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(
                        MethodArgumentNotValidException ex) {

                Map<String, String> validationErrors = new LinkedHashMap<>();

                ex.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> validationErrors.put(
                                                error.getField(),
                                                error.getDefaultMessage()));

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("timestamp", LocalDateTime.now());
                response.put("status", HttpStatus.BAD_REQUEST.value());
                response.put("error", "Validation Failed");
                response.put("errors", validationErrors);

                return ResponseEntity
                                .badRequest()
                                .body(response);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalArgument(
                        IllegalArgumentException ex) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("timestamp", LocalDateTime.now());
                response.put("status", HttpStatus.BAD_REQUEST.value());
                response.put("error", "Bad Request");
                response.put("message", ex.getMessage());

                return ResponseEntity
                                .badRequest()
                                .body(response);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGenericException(
                        Exception ex) {

                log.error(
                                "Unhandled server exception caught by GlobalExceptionHandler",
                                ex);

                Map<String, Object> error = new LinkedHashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
                error.put("error", "Internal Server Error");
                error.put(
                                "message",
                                "An unexpected error occurred. Please try again later.");

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error);
        }
}
