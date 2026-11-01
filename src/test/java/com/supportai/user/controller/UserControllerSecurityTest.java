package com.supportai.user.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.dto.AdminCreateUserRequest;
import com.supportai.user.dto.AgentResponse;
import com.supportai.user.dto.UpdateUserRoleRequest;
import com.supportai.user.dto.UserProfileResponse;
import com.supportai.user.dto.UserSummaryResponse;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // --- /api/users/agents tests ---

    @Test
    void unauthenticatedUserCannotRetrieveAgents() throws Exception {
        mockMvc.perform(get("/api/users/agents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotRetrieveAgents() throws Exception {
        mockMvc.perform(get("/api/users/agents")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCanRetrieveAgents() throws Exception {
        AgentResponse agent = new AgentResponse(
                2L,
                "Agent Smith",
                "smith@supportai.com",
                Role.AGENT
        );

        when(userService.getAgents()).thenReturn(List.of(agent));

        mockMvc.perform(get("/api/users/agents")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Agent Smith"))
                .andExpect(jsonPath("$[0].email").value("smith@supportai.com"))
                .andExpect(jsonPath("$[0].role").value("AGENT"));
    }

    @Test
    void adminCanRetrieveAgents() throws Exception {
        AgentResponse agent = new AgentResponse(
                2L,
                "Agent Smith",
                "smith@supportai.com",
                Role.AGENT
        );

        when(userService.getAgents()).thenReturn(List.of(agent));

        mockMvc.perform(get("/api/users/agents")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Agent Smith"))
                .andExpect(jsonPath("$[0].email").value("smith@supportai.com"))
                .andExpect(jsonPath("$[0].role").value("AGENT"));
    }

    @Test
    void emptyAgentListReturnsOkWithEmptyArray() throws Exception {
        when(userService.getAgents()).thenReturn(List.of());

        mockMvc.perform(get("/api/users/agents")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(content().json("[]"));
    }

    @Test
    void responseContainsExpectedFieldsAndDoesNotExposePasswordOrHash() throws Exception {
        AgentResponse agent = new AgentResponse(
                5L,
                "Bob Agent",
                "bob@supportai.com",
                Role.AGENT
        );

        when(userService.getAgents()).thenReturn(List.of(agent));

        mockMvc.perform(get("/api/users/agents")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("Bob Agent"))
                .andExpect(jsonPath("$[0].email").value("bob@supportai.com"))
                .andExpect(jsonPath("$[0].role").value("AGENT"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    // --- /api/users/me tests ---

    @Test
    void unauthenticatedUserCannotAccessMeEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomerCanAccessMeEndpoint() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(
                1L,
                "Customer User",
                "customer@supportai.com",
                Role.CUSTOMER,
                LocalDateTime.of(2026, 1, 15, 10, 0)
        );

        when(userService.getUserProfile(1L)).thenReturn(profile);

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Customer User"))
                .andExpect(jsonPath("$.email").value("customer@supportai.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void authenticatedAdminCanAccessMeEndpoint() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(
                99L,
                "Admin User",
                "admin@supportai.com",
                Role.ADMIN,
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        when(userService.getUserProfile(99L)).thenReturn(profile);

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // --- GET /api/users tests ---

    @Test
    void unauthenticatedUserCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        UserSummaryResponse summary = new UserSummaryResponse(
                1L,
                "Test User",
                "test@supportai.com",
                Role.CUSTOMER,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );
        PageResponse<UserSummaryResponse> pageResponse = new PageResponse<>(
                List.of(summary),
                0,
                20,
                1L,
                1,
                true,
                true
        );

        when(userService.getUsers(anyInt(), anyInt(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].email").value("test@supportai.com"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    // --- POST /api/users tests ---

    @Test
    void unauthenticatedUserCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "New User",
                                    "email": "new@supportai.com",
                                    "password": "Password123!",
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "New User",
                                    "email": "new@supportai.com",
                                    "password": "Password123!",
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "New User",
                                    "email": "new@supportai.com",
                                    "password": "Password123!",
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUser() throws Exception {
        UserSummaryResponse response = new UserSummaryResponse(
                50L,
                "New User",
                "new@supportai.com",
                Role.AGENT,
                LocalDateTime.of(2026, 2, 1, 10, 0),
                LocalDateTime.of(2026, 2, 1, 10, 0)
        );

        when(userService.createUser(any(AdminCreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "New User",
                                    "email": "new@supportai.com",
                                    "password": "Password123!",
                                    "role": "AGENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.name").value("New User"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // --- PATCH /api/users/{id}/role tests ---

    @Test
    void unauthenticatedUserCannotUpdateUserRole() throws Exception {
        mockMvc.perform(patch("/api/users/5/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUpdateUserRole() throws Exception {
        mockMvc.perform(patch("/api/users/5/role")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCannotUpdateUserRole() throws Exception {
        mockMvc.perform(patch("/api/users/5/role")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateUserRole() throws Exception {
        UserSummaryResponse response = new UserSummaryResponse(
                5L,
                "Updated User",
                "user5@supportai.com",
                Role.AGENT,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 2, 1, 12, 0)
        );

        when(userService.updateUserRole(eq(5L), any(UpdateUserRoleRequest.class), eq(99L))).thenReturn(response);

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }
}
