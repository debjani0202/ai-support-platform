package com.supportai.user.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.security.SecurityConfig;
import com.supportai.user.dto.AdminCreateUserRequest;
import com.supportai.user.dto.UpdateUserRoleRequest;
import com.supportai.user.entity.Role;
import com.supportai.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createUser_rejectsInvalidPayloadWithValidationErrors() throws Exception {
        // Missing name, invalid email, short password, null role
        mockMvc.perform(post("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "email": "invalid-email",
                                    "password": "short",
                                    "role": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    void createUser_mapsDuplicateEmailToBadRequest() throws Exception {
        when(userService.createUser(any(AdminCreateUserRequest.class)))
                .thenThrow(new IllegalArgumentException("Email is already registered"));

        mockMvc.perform(post("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Jane",
                                    "email": "duplicate@supportai.com",
                                    "password": "Password123!",
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void updateUserRole_rejectsNullRole() throws Exception {
        String invalidPayload = "{\"role\": null}";

        mockMvc.perform(patch("/api/users/10/role")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUserRole_mapsSelfDemotionToBadRequest() throws Exception {
        when(userService.updateUserRole(eq(99L), any(UpdateUserRoleRequest.class), eq(99L)))
                .thenThrow(new IllegalArgumentException("Administrators cannot demote their own account"));

        mockMvc.perform(patch("/api/users/99/role")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Administrators cannot demote their own account"));
    }

    @Test
    void updateUserRole_mapsLastAdminDemotionToBadRequest() throws Exception {
        when(userService.updateUserRole(eq(5L), any(UpdateUserRoleRequest.class), eq(99L)))
                .thenThrow(new IllegalArgumentException("Cannot demote the last remaining administrator"));

        mockMvc.perform(patch("/api/users/5/role")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot demote the last remaining administrator"));
    }

    @Test
    void updateUserRole_returnsNotFoundWhenTargetDoesNotExist() throws Exception {
        when(userService.updateUserRole(eq(999L), any(UpdateUserRoleRequest.class), eq(99L)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(patch("/api/users/999/role")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id: 999"));
    }

    @Test
    void getUserProfile_returnsNotFoundWhenProfileDoesNotExist() throws Exception {
        when(userService.getUserProfile(1L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 1"));

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }
}
