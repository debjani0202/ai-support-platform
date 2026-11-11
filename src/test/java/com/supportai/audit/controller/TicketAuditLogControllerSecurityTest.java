package com.supportai.audit.controller;

import com.supportai.audit.dto.TicketAuditLogResponse;
import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.entity.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        PageResponse<TicketAuditLogResponse> emptyPage = new PageResponse<>(
                List.of(), 0, 20, 0, 0, true, true
        );

        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(20)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getHistory_withExplicitPaginationParameters_forwardsToService() throws Exception {
        TicketAuditLogResponse log = new TicketAuditLogResponse(
                5L, 1L, 1L, "Author", "STATUS_CHANGED", "OPEN", "IN_PROGRESS", LocalDateTime.now()
        );
        PageResponse<TicketAuditLogResponse> pageResponse = new PageResponse<>(
                List.of(log), 1, 10, 15, 2, false, true
        );

        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(1), eq(10)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .param("page", "1")
                        .param("size", "10")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getHistory_withNegativePage_returns400() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(-1), eq(20)))
                .thenThrow(new IllegalArgumentException("Page number cannot be negative"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .param("page", "-1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_withZeroSize_returns400() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(0)))
                .thenThrow(new IllegalArgumentException("Page size must be between 1 and 100"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .param("size", "0")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_withSizeExceeding100_returns400() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(101)))
                .thenThrow(new IllegalArgumentException("Page size must be between 1 and 100"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .param("size", "101")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedAgentCanGetAuditHistory() throws Exception {
        PageResponse<TicketAuditLogResponse> emptyPage = new PageResponse<>(
                List.of(), 0, 20, 0, 0, true, true
        );

        when(auditLogService.getHistory(eq(1L), eq(2L), eq(Role.AGENT), eq(0), eq(20)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedAdminCanGetAuditHistory() throws Exception {
        PageResponse<TicketAuditLogResponse> emptyPage = new PageResponse<>(
                List.of(), 0, 20, 0, 0, true, true
        );

        when(auditLogService.getHistory(eq(1L), eq(3L), eq(Role.ADMIN), eq(0), eq(20)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("3")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedUserReceivesForbidden() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(20)))
                .thenThrow(new ForbiddenException("You are not allowed to access this ticket's audit history"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanGetAuditHistoryForSoftDeletedTicket() throws Exception {
        TicketAuditLogResponse deletedEvent = new TicketAuditLogResponse(
                10L, 1L, 3L, "admin", "DELETED", null, null, LocalDateTime.now()
        );
        PageResponse<TicketAuditLogResponse> pageResponse = new PageResponse<>(
                List.of(deletedEvent), 0, 20, 1, 1, true, true
        );
        when(auditLogService.getHistory(eq(1L), eq(3L), eq(Role.ADMIN), eq(0), eq(20)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("3")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void customerReceivesNotFoundForSoftDeletedTicket() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(20)))
                .thenThrow(new ResourceNotFoundException("Ticket not found with ID: 1"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentReceivesNotFoundForSoftDeletedTicket() throws Exception {
        when(auditLogService.getHistory(eq(1L), eq(2L), eq(Role.AGENT), eq(0), eq(20)))
                .thenThrow(new ResourceNotFoundException("Ticket not found with ID: 1"));

        mockMvc.perform(get("/api/tickets/1/audit-logs")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isNotFound());
    }
}
