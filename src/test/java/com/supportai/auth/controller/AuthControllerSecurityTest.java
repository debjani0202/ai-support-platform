
package com.supportai.auth.controller;

import com.supportai.auth.service.AuthService;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
