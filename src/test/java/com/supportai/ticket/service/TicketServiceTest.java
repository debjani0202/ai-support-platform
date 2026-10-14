
package com.supportai.ticket.service;

import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServiceTest {

    private TicketRepository ticketRepository;
    private UserRepository userRepository;
    private TicketAuditLogService auditLogService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        // Mock dependencies so these unit tests don't need a real database.
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogService = mock(TicketAuditLogService.class);

        // TicketService now requires all three dependencies.
        ticketService = new TicketService(
                ticketRepository,
                userRepository,
                auditLogService
        );
    }

    @Test
    void ticketRepositoryMockShouldExist() {
        assertNotNull(ticketRepository);
    }

    @Test
    void userRepositoryMockShouldExist() {
        assertNotNull(userRepository);
    }

    @Test
    void auditLogServiceMockShouldExist() {
        assertNotNull(auditLogService);
    }

    @Test
    void ticketServiceShouldBeCreated() {
        assertNotNull(ticketService);
    }
}
