package com.supportai.auth.controller;

import com.supportai.auth.dto.AuthResponse;
import com.supportai.auth.dto.LoginRequest;
import com.supportai.auth.dto.LoginResponse;
import com.supportai.auth.dto.RegisterRequest;
import com.supportai.auth.service.AuthService;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.user.entity.Role;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

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
    void loginEndpointReturnsOkWithCorrectJsonStructure() throws Exception {
        LoginResponse response = new LoginResponse(
                1L,
                "Jane Doe",
                "jane@example.com",
                Role.CUSTOMER,
                "test-jwt-token",
                "Bearer"
        );

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

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
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
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
}
