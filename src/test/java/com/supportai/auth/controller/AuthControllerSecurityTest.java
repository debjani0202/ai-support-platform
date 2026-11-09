
package com.supportai.auth.controller;

import com.supportai.auth.dto.AuthResponse;
import com.supportai.auth.dto.AuthResult;
import com.supportai.auth.dto.LoginRequest;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.dto.RegisterRequest;
import com.supportai.auth.dto.ForgotPasswordRequest;
import com.supportai.auth.dto.ForgotPasswordResponse;
import com.supportai.auth.dto.ResetPasswordRequest;
import com.supportai.auth.dto.VerifyResetTokenResponse;
import com.supportai.auth.service.AuthService;
import com.supportai.auth.service.PasswordResetService;
import com.supportai.auth.service.RefreshTokenService;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.exception.InvalidRefreshTokenException;
import com.supportai.exception.TokenAlreadyRotatedException;
import com.supportai.security.SecurityConfig;
import com.supportai.user.entity.Role;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerSecurityTest {

    private static final String APPROVED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void registerEndpointReturnsCreatedWithCorrectJsonStructure() throws Exception {

        AuthResponse response = new AuthResponse(
                1L,
                "Jane Doe",
                "jane@example.com",
                Role.CUSTOMER
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Jane Doe",
                                    "email": "jane@example.com",
                                    "password": "Password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void loginEndpointReturnsOkWithCorrectJsonStructureAndCookie() throws Exception {

        LoginResponse response = new LoginResponse(
                1L,
                "Jane Doe",
                "jane@example.com",
                Role.CUSTOMER,
                "test-jwt-token",
                "Bearer"
        );

        AuthResult authResult =
                new AuthResult(response, "test-raw-refresh-token");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(authResult);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "jane@example.com",
                                    "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.accessToken").value("test-jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(content().string(
                        not(containsString("test-raw-refresh-token"))))
                .andExpect(header().string("Set-Cookie",
                        containsString("refreshToken=test-raw-refresh-token")))
                .andExpect(header().string("Set-Cookie",
                        containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie",
                        containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",
                        containsString("SameSite=Lax")));
    }

    @Test
    void unauthenticatedUserCannotChangePassword() throws Exception {

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "OldPassword123",
                                    "newPassword": "NewPassword123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithBlankCurrentPasswordGetsBadRequest()
            throws Exception {

        mockMvc.perform(post("/api/auth/change-password")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": " ",
                                    "newPassword": "NewPassword123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedUserWithShortNewPasswordGetsBadRequest()
            throws Exception {

        mockMvc.perform(post("/api/auth/change-password")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "OldPassword123",
                                    "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithPasswordExceeding72CharactersReturnsBadRequest()
            throws Exception {

        String longPassword = "A".repeat(73);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Jane Doe",
                                    "email": "jane@example.com",
                                    "password": "%s"
                                }
                                """.formatted(longPassword)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedUserWithNewPasswordExceeding72CharactersGetsBadRequest()
            throws Exception {

        String longPassword = "B".repeat(73);

        mockMvc.perform(post("/api/auth/change-password")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "OldPassword123",
                                    "newPassword": "%s"
                                }
                                """.formatted(longPassword)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordEndpointIsPublicAndReturnsOk() throws Exception {

        when(passwordResetService.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenReturn(new ForgotPasswordResponse(
                        "If an account exists with this email address, a password reset link has been sent."));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists with this email address, a password reset link has been sent."));
    }

    @Test
    void forgotPasswordWithInvalidEmailReturnsBadRequest() throws Exception {

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyResetTokenEndpointIsPublicAndReturnsOk() throws Exception {

        when(passwordResetService.verifyResetToken("test-token"))
                .thenReturn(new VerifyResetTokenResponse(true, "Token is valid"));

        mockMvc.perform(get("/api/auth/verify-reset-token")
                        .param("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"));
    }

    @Test
    void resetPasswordEndpointIsPublicAndReturnsNoContent() throws Exception {

        doNothing().when(passwordResetService)
                .resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "valid-token-123",
                                    "newPassword": "BrandNewPassword123"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPasswordWithShortPasswordReturnsBadRequest() throws Exception {

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "valid-token-123",
                                    "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordWithBlankTokenReturnsBadRequest() throws Exception {

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "",
                                    "newPassword": "BrandNewPassword123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_missingCookie_returnsUnauthorized() throws Exception {

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Origin", APPROVED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Refresh token is required"));
    }

    @Test
    void refreshToken_withValidCookie_returnsOkWithLoginResponseAndNewCookie()
            throws Exception {

        LoginResponse response = new LoginResponse(
                10L,
                "Customer Name",
                "customer@support.ai",
                Role.AGENT,
                "new-jwt-token",
                "Bearer"
        );

        AuthResult authResult =
                new AuthResult(response, "new-rotated-refresh-token");

        when(refreshTokenService.rotateRefreshToken("valid-raw-refresh-token"))
                .thenReturn(authResult);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Origin", APPROVED_ORIGIN)
                        .cookie(new Cookie(
                                "refreshToken", "valid-raw-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.accessToken").value("new-jwt-token"))
                .andExpect(content().string(
                        not(containsString("new-rotated-refresh-token"))))
                .andExpect(header().string("Set-Cookie",
                        containsString("refreshToken=new-rotated-refresh-token")))
                .andExpect(header().string("Set-Cookie",
                        containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie",
                        containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",
                        containsString("SameSite=Lax")));
    }

    @Test
    void refreshToken_withValidReferer_returnsOk() throws Exception {

        LoginResponse response = new LoginResponse(
                10L,
                "Customer Name",
                "customer@support.ai",
                Role.AGENT,
                "new-jwt-token",
                "Bearer"
        );

        AuthResult authResult =
                new AuthResult(response, "new-rotated-refresh-token");

        when(refreshTokenService.rotateRefreshToken("valid-raw-refresh-token"))
                .thenReturn(authResult);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Referer", APPROVED_ORIGIN + "/login")
                        .cookie(new Cookie(
                                "refreshToken", "valid-raw-refresh-token")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshToken_withoutOriginOrReferer_returnsForbidden() throws Exception {

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", "some-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshToken_withInvalidOrigin_returnsForbidden() throws Exception {

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Origin", "https://unauthorized-domain.com")
                        .cookie(new Cookie("refreshToken", "some-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshToken_tokenAlreadyRotated_returnsConflictWithCode()
            throws Exception {

        when(refreshTokenService.rotateRefreshToken("already-rotated-token"))
                .thenThrow(new TokenAlreadyRotatedException(
                        "Token was rotated by a concurrent request"));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Origin", APPROVED_ORIGIN)
                        .cookie(new Cookie(
                                "refreshToken", "already-rotated-token")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_ALREADY_ROTATED"))
                .andExpect(jsonPath("$.message").value(
                        "Token was rotated by a concurrent request"));
    }

    @Test
    void refreshToken_expiredToken_returnsUnauthorizedAndClearsCookie()
            throws Exception {

        when(refreshTokenService.rotateRefreshToken("expired-token"))
                .thenThrow(new InvalidRefreshTokenException(
                        InvalidRefreshTokenException.Reason.TOKEN_EXPIRED,
                        "Refresh token has expired"));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Origin", APPROVED_ORIGIN)
                        .cookie(new Cookie("refreshToken", "expired-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.reason").value("TOKEN_EXPIRED"))
                .andExpect(header().string("Set-Cookie",
                        containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie",
                        containsString("Max-Age=0")));
    }

    @Test
    void logout_clearsCookieAndReturnsNoContent() throws Exception {

        mockMvc.perform(post("/api/auth/logout")
                        .header("Origin", APPROVED_ORIGIN)
                        .cookie(new Cookie(
                                "refreshToken", "valid-logout-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie",
                        containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie",
                        containsString("Max-Age=0")));

        verify(refreshTokenService).logout("valid-logout-token");
    }

    @Test
    void logout_withoutCookie_stillReturnsNoContentAndClearsCookie()
            throws Exception {

        mockMvc.perform(post("/api/auth/logout")
                        .header("Origin", APPROVED_ORIGIN))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie",
                        containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie",
                        containsString("Max-Age=0")));
    }

    @Test
    void logout_withoutOriginOrReferer_returnsForbidden() throws Exception {

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_withInvalidOrigin_returnsForbidden() throws Exception {

        mockMvc.perform(post("/api/auth/logout")
                        .header("Origin", "https://unauthorized-domain.com"))
                .andExpect(status().isForbidden());
    }
}
