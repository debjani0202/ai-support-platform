package com.supportai.user.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.user.dto.AgentResponse;
import com.supportai.user.entity.Role;
import com.supportai.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
