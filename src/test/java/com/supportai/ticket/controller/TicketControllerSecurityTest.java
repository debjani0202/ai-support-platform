
package com.supportai.ticket.controller;

import com.supportai.security.SecurityConfig;
import com.supportai.ticket.service.TicketService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock the service so this controller test does not require a database.
    @MockitoBean
    private TicketService ticketService;

    // Provide a mock JWT decoder required by the production security configuration.
    // This test checks unauthenticated access, so it does not need to decode a real token.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticatedUserCannotAccessTickets() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }
}
