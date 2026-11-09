
package com.supportai.auth.controller;

import com.supportai.auth.dto.AuthResponse;
import com.supportai.auth.dto.AuthResult;
import com.supportai.auth.dto.ChangePasswordRequest;
import com.supportai.auth.dto.ForgotPasswordRequest;
import com.supportai.auth.dto.ForgotPasswordResponse;
import com.supportai.auth.dto.LoginRequest;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.dto.RegisterRequest;
import com.supportai.auth.dto.ResetPasswordRequest;
import com.supportai.auth.dto.VerifyResetTokenResponse;
import com.supportai.auth.service.AuthService;
import com.supportai.auth.service.PasswordResetService;
import com.supportai.auth.service.RefreshTokenService;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.InvalidRefreshTokenException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;

    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final long refreshTokenExpirationDays;
    private final List<String> allowedOrigins;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            RefreshTokenService refreshTokenService,
            @Value("${app.auth.cookie.name:refreshToken}") String cookieName,
            @Value("${app.auth.cookie.secure:false}") boolean cookieSecure,
            @Value("${app.auth.cookie.same-site:Lax}") String cookieSameSite,
            @Value("${app.auth.refresh-token.expiration-days:7}") long refreshTokenExpirationDays,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:5176,http://localhost:4173}") List<String> allowedOrigins) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
        this.allowedOrigins = allowedOrigins;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResult authResult = authService.login(request);

        ResponseCookie cookie = createRefreshTokenCookie(
                authResult.rawRefreshToken());

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(authResult.loginResponse());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        validateOriginOrReferer(request);

        String rawRefreshToken = extractRefreshTokenCookie(request);

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException(
                    InvalidRefreshTokenException.Reason.INVALID_TOKEN,
                    "Refresh token is required");
        }

        AuthResult authResult = refreshTokenService.rotateRefreshToken(
                rawRefreshToken);

        ResponseCookie cookie = createRefreshTokenCookie(
                authResult.rawRefreshToken());

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(authResult.loginResponse());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        validateOriginOrReferer(request);

        String rawRefreshToken = extractRefreshTokenCookie(request);

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.logout(rawRefreshToken);
        }

        ResponseCookie clearCookie = clearRefreshTokenCookie();

        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        // Get the user ID from the authenticated JWT principal.
        Long userId = Long.valueOf(authentication.getName());

        authService.changePassword(userId, request);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return passwordResetService.forgotPassword(request);
    }

    @GetMapping("/verify-reset-token")
    public VerifyResetTokenResponse verifyResetToken(
            @RequestParam String token) {
        return passwordResetService.verifyResetToken(token);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }

    private void validateOriginOrReferer(HttpServletRequest request) {

        String origin = request.getHeader("Origin");

        // If Origin is present, validate it directly.
        if (origin != null && !origin.isBlank()) {
            if (!isAllowedOrigin(origin.trim())) {
                throw new ForbiddenException("Invalid request origin");
            }
            return;
        }

        // If Origin is absent, fall back to Referer.
        String referer = request.getHeader("Referer");

        if (referer == null || referer.isBlank()) {
            throw new ForbiddenException(
                    "Missing Origin and Referer headers");
        }

        try {
            URI uri = URI.create(referer.trim());

            // Referer must be an absolute HTTP or HTTPS URL.
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawAuthority() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))) {

                throw new ForbiddenException("Malformed request referer");
            }

            String refererOrigin = uri.getScheme()
                    + "://"
                    + uri.getRawAuthority();

            if (!isAllowedOrigin(refererOrigin)) {
                throw new ForbiddenException("Invalid request referer");
            }

        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Malformed request referer");
        }
    }

    private boolean isAllowedOrigin(String candidate) {

        if (candidate == null || candidate.isBlank()) {
            return false;
        }

        return allowedOrigins.stream()
                .filter(allowed -> allowed != null && !allowed.isBlank())
                .filter(allowed -> !"*".equals(allowed.trim()))
                .anyMatch(allowed -> allowed.trim().equalsIgnoreCase(candidate.trim()));
    }

    private ResponseCookie createRefreshTokenCookie(String rawRefreshToken) {

        return ResponseCookie.from(cookieName, rawRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(Duration.ofDays(refreshTokenExpirationDays))
                .build();
    }

    private ResponseCookie clearRefreshTokenCookie() {

        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
