package com.supportai.ticket.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.service.TicketService;
import com.supportai.user.entity.Role;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // --- Unauthenticated Requests (401 Unauthorized) ---

    @Test
    void unauthenticatedUserCannotAccessTickets() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUserCannotGetTicketById() throws Exception {
        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUserCannotCreateTicket() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Unauthenticated Ticket",
                                    "description": "Attempt to create without auth",
                                    "priority": "MEDIUM"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUserCannotUpdateTicket() throws Exception {
        mockMvc.perform(put("/api/tickets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Title",
                                    "description": "Updated Description",
                                    "status": "IN_PROGRESS",
                                    "priority": "HIGH",
                                    "category": "TECHNICAL_ISSUE"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUserCannotDeleteTicket() throws Exception {
        mockMvc.perform(delete("/api/tickets/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUserCannotAssignTicket() throws Exception {
        mockMvc.perform(patch("/api/tickets/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "agentId": 2
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // --- Role-Based Authorization: Ticket Assignment ---

    @Test
    void customerCannotAssignTicket() throws Exception {
        mockMvc.perform(patch("/api/tickets/1/assign")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "agentId": 2
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCannotAssignTicket() throws Exception {
        mockMvc.perform(patch("/api/tickets/1/assign")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "agentId": 3
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAssignTicket() throws Exception {
        TicketResponse dummyResponse = new TicketResponse(
                1L, "Test Ticket", "Description", "OPEN",
                TicketCategory.GENERAL_QUERY, TicketPriority.LOW,
                1L, 2L, LocalDateTime.now(), LocalDateTime.now()
        );

        when(ticketService.assignTicket(eq(1L), eq(2L), eq(99L), eq(Role.ADMIN)))
                .thenReturn(dummyResponse);

        mockMvc.perform(patch("/api/tickets/1/assign")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "agentId": 2
                                }
                                """))
                .andExpect(status().isOk());
    }

    // --- Role-Based Authorization: Ticket Deletion ---

    @Test
    void customerCannotDeleteTicket() throws Exception {
        mockMvc.perform(delete("/api/tickets/1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentCanRequestDeleteTicket() throws Exception {
        mockMvc.perform(delete("/api/tickets/1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCanDeleteTicket() throws Exception {
        mockMvc.perform(delete("/api/tickets/1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99")
                                .claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    // --- General Authenticated Access ---

    @Test
    void customerCanGetTickets() throws Exception {
        when(ticketService.getTickets(
                anyInt(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(1L), eq(Role.CUSTOMER)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

        mockMvc.perform(get("/api/tickets")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }
}
