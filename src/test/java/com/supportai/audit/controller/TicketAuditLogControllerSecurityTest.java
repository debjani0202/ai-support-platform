package com.supportai.audit.controller;

import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.user.entity.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketAuditLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketAuditLogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketAuditLogService auditLogService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticatedUserCannotGetAuditHistory() throws Exception {
        mockMvc.perform(get("/api/tickets/1/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomerCanGetAuditHistory() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedAgentCanGetAuditHistory() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(2L), eq(Role.AGENT)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedAdminCanGetAuditHistory() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(3L), eq(Role.ADMIN)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("3")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedUserReceivesForbidden() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER)))
                .thenThrow(new ForbiddenException("You are not allowed to access this ticket's audit history"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }
}
