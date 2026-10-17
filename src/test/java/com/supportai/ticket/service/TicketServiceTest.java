
package com.supportai.ticket.service;

import com.supportai.ticket.dto.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.notification.service.NotificationService;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.dto.CreateTicketRequest;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.dto.UpdateTicketRequest;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketServiceTest {

    private TicketRepository ticketRepository;
    private UserRepository userRepository;
    private TicketAuditLogService auditLogService;
    private NotificationService notificationService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogService = mock(TicketAuditLogService.class);
        notificationService = mock(NotificationService.class);

        ticketService = new TicketService(
                ticketRepository,
                userRepository,
                auditLogService,
                notificationService);
    }

    @Test
    void createTicketShouldSaveTicketAndRecordAuditEvent() {
        // Arrange
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        CreateTicketRequest request = new CreateTicketRequest(
                "Cannot log in",
                "I cannot access my account.",
                TicketCategory.ACCOUNT_ACCESS,
                TicketPriority.HIGH
        );

        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TicketResponse response =
                ticketService.createTicket(request, customerId);

        // Assert
        verify(userRepository).findById(customerId);
        verify(ticketRepository).save(any(Ticket.class));

        assertEquals("Cannot log in", response.title());
        assertEquals(
                "I cannot access my account.",
                response.description()
        );
        assertEquals("OPEN", response.status());
        assertEquals(
                TicketCategory.ACCOUNT_ACCESS,
                response.category()
        );
        assertEquals(TicketPriority.HIGH, response.priority());
        assertEquals(customerId, response.createdBy());
        assertNull(response.assignedTo());

        verify(auditLogService).recordAction(
                any(Ticket.class),
                eq(customer),
                eq("CREATED"),
                isNull(),
                eq("Ticket created: Cannot log in")
        );
    }

    @Test
    void createTicketShouldFailWhenCustomerDoesNotExist() {
        // Arrange
        Long customerId = 999L;

        when(userRepository.findById(customerId))
                .thenReturn(Optional.empty());

        CreateTicketRequest request = new CreateTicketRequest(
                "Cannot log in",
                "I cannot access my account.",
                TicketCategory.ACCOUNT_ACCESS,
                TicketPriority.HIGH
        );

        // Act and Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> ticketService.createTicket(request, customerId)
        );

        verify(userRepository).findById(customerId);
        verify(ticketRepository, never()).save(any(Ticket.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void customerShouldNotAccessAnotherCustomersTicket() {
        // Arrange
        Long ticketId = 100L;
        Long ownerId = 10L;
        Long anotherCustomerId = 20L;

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);

        Ticket ticket = new Ticket(
                "Private ticket",
                "Customer-specific details",
                "OPEN"
        );
        ticket.setCreatedBy(owner);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));

        // Act and Assert
        assertThrows(
                ForbiddenException.class,
                () -> ticketService.getTicketById(
                        ticketId,
                        anotherCustomerId,
                        Role.CUSTOMER
                )
        );

        verify(ticketRepository).findById(ticketId);
    }

    @Test
    void nonAdminShouldNotAssignTicket() {
        // Arrange
        Long ticketId = 100L;
        Long agentId = 30L;
        Long customerId = 10L;

        // Act and Assert
        assertThrows(
                ForbiddenException.class,
                () -> ticketService.assignTicket(
                        ticketId,
                        agentId,
                        customerId,
                        Role.CUSTOMER
                )
        );

        // Permission must be checked before database access.
        verifyNoInteractions(ticketRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void customerShouldNotDeleteTicket() {
        // Arrange
        Long ticketId = 100L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket(
                "My ticket",
                "Ticket details",
                "OPEN"
        );
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));

        // Act and Assert
        assertThrows(
                ForbiddenException.class,
                () -> ticketService.deleteTicket(
                        ticketId,
                        customerId,
                        Role.CUSTOMER
                )
        );

        verify(ticketRepository).findById(ticketId);
        verify(ticketRepository, never()).delete(any(Ticket.class));
    }

    @Test
    void agentShouldNotAccessTicketAssignedToAnotherAgent() {
        // Arrange
        Long ticketId = 100L;
        Long assignedAgentId = 30L;
        Long anotherAgentId = 40L;

        User assignedAgent = mock(User.class);
        when(assignedAgent.getId()).thenReturn(assignedAgentId);

        Ticket ticket = new Ticket(
                "Technical issue",
                "Application is not responding.",
                "OPEN"
        );
        ticket.setAssignedTo(assignedAgent);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));

        // Act and Assert
        assertThrows(
                ForbiddenException.class,
                () -> ticketService.getTicketById(
                        ticketId,
                        anotherAgentId,
                        Role.AGENT
                )
        );

        verify(ticketRepository).findById(ticketId);
    }

    @Test
    void adminShouldAccessAnyTicket() {
        // Arrange
        Long ticketId = 100L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(10L);

        Ticket ticket = new Ticket(
                "Production issue",
                "Service is unavailable.",
                "OPEN"
        );
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));

        // Act
        TicketResponse response = ticketService.getTicketById(
                ticketId,
                99L,
                Role.ADMIN
        );

        // Assert
        assertEquals("Production issue", response.title());
        assertEquals(10L, response.createdBy());
        verify(ticketRepository).findById(ticketId);
    }

    @Test
    void adminShouldAssignTicketToAgentAndRecordAuditEvent() {
        // Arrange
        Long ticketId = 100L;
        Long adminId = 1L;
        Long agentId = 30L;
        Long customerId = 10L;

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(adminId);

        User agent = mock(User.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getRole()).thenReturn(Role.AGENT);

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket(
                "Technical issue",
                "Application is not responding.",
                "OPEN"
        );
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(agentId))
                .thenReturn(Optional.of(agent));
        when(userRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TicketResponse response = ticketService.assignTicket(
                ticketId,
                agentId,
                adminId,
                Role.ADMIN
        );

        // Assert
        assertEquals(agentId, response.assignedTo());
        assertEquals(customerId, response.createdBy());

        verify(ticketRepository).save(ticket);
        verify(auditLogService).recordAction(
                eq(ticket),
                eq(admin),
                eq("ASSIGNED"),
                isNull(),
                eq(agentId.toString())
        );
    }

    @Test
void adminShouldNotAssignTicketToNonAgent() {
    // Arrange
    Long ticketId = 100L;
    Long adminId = 1L;
    Long customerId = 20L;

    User customer = mock(User.class);
    when(customer.getId()).thenReturn(customerId);
    when(customer.getRole()).thenReturn(Role.CUSTOMER);

    Ticket ticket = new Ticket(
            "Technical issue",
            "Application is not responding.",
            "OPEN"
    );

    User creator = mock(User.class);
    when(creator.getId()).thenReturn(10L);
    ticket.setCreatedBy(creator);

    when(ticketRepository.findById(ticketId))
            .thenReturn(Optional.of(ticket));
    when(userRepository.findById(customerId))
            .thenReturn(Optional.of(customer));

    // Act and Assert
    assertThrows(
            IllegalArgumentException.class,
            () -> ticketService.assignTicket(
                    ticketId,
                    customerId,
                    adminId,
                    Role.ADMIN
            )
    );

    verify(ticketRepository, never()).save(any(Ticket.class));
    verifyNoInteractions(auditLogService);
}
@Test
void adminShouldNotAssignTicketToNonexistentAgent() {
    // Arrange
    Long ticketId = 100L;
    Long adminId = 1L;
    Long agentId = 999L;

    Ticket ticket = new Ticket(
            "Technical issue",
            "Application is not responding.",
            "OPEN"
    );

    User creator = mock(User.class);
    when(creator.getId()).thenReturn(10L);
    ticket.setCreatedBy(creator);

    when(ticketRepository.findById(ticketId))
            .thenReturn(Optional.of(ticket));
    when(userRepository.findById(agentId))
            .thenReturn(Optional.empty());

    // Act and Assert
    assertThrows(
            ResourceNotFoundException.class,
            () -> ticketService.assignTicket(
                    ticketId,
                    agentId,
                    adminId,
                    Role.ADMIN
            )
    );

    verify(ticketRepository, never()).save(any(Ticket.class));
    verifyNoInteractions(auditLogService);
}

@Test
void adminShouldNotAssignNonexistentTicket() {
    // Arrange
    Long ticketId = 999L;
    Long agentId = 30L;
    Long adminId = 1L;

    when(ticketRepository.findById(ticketId))
            .thenReturn(Optional.empty());

    // Act and Assert
    assertThrows(
            ResourceNotFoundException.class,
            () -> ticketService.assignTicket(
                    ticketId,
                    agentId,
                    adminId,
                    Role.ADMIN
            )
    );

    verify(ticketRepository).findById(ticketId);
    verify(userRepository, never()).findById(anyLong());
    verify(ticketRepository, never()).save(any(Ticket.class));
    verifyNoInteractions(auditLogService);
}

@Test
void customerShouldNotChangeTicketStatus() {
    // Arrange
    Long ticketId = 100L;
    Long customerId = 10L;

    User customer = mock(User.class);
    when(customer.getId()).thenReturn(customerId);

    Ticket ticket = new Ticket(
            "Cannot log in",
            "I cannot access my account.",
            "OPEN"
    );
    ticket.setCreatedBy(customer);

    when(ticketRepository.findById(ticketId))
            .thenReturn(Optional.of(ticket));

    UpdateTicketRequest request = new UpdateTicketRequest(
            "Cannot log in",
            "I cannot access my account.",
            "RESOLVED",
            TicketCategory.ACCOUNT_ACCESS,
            TicketPriority.HIGH
    );

    // Act and Assert
    assertThrows(
            ForbiddenException.class,
            () -> ticketService.updateTicket(
                    ticketId,
                    request,
                    customerId,
                    Role.CUSTOMER
            )
    );

    // The ticket must not be saved or audited.
    assertEquals("OPEN", ticket.getStatus());
    verify(ticketRepository, never()).save(any(Ticket.class));
    verifyNoInteractions(auditLogService);
}

@Test
void agentShouldUpdateTicketStatusAndRecordAuditEvent() {
    // Arrange
    Long ticketId = 100L;
    Long agentId = 30L;
    Long customerId = 10L;

    User customer = mock(User.class);
    when(customer.getId()).thenReturn(customerId);

    User agent = mock(User.class);
    when(agent.getId()).thenReturn(agentId);

    Ticket ticket = new Ticket(
            "Cannot log in",
            "I cannot access my account.",
            "OPEN"
    );
    ticket.setCreatedBy(customer);
    ticket.setAssignedTo(agent);

    when(ticketRepository.findById(ticketId))
            .thenReturn(Optional.of(ticket));
    when(userRepository.findById(agentId))
            .thenReturn(Optional.of(agent));
    when(ticketRepository.save(any(Ticket.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    UpdateTicketRequest request = new UpdateTicketRequest(
            "Cannot log in",
            "Issue investigated by support.",
            "IN_PROGRESS",
            TicketCategory.ACCOUNT_ACCESS,
            TicketPriority.HIGH
    );

    // Act
    TicketResponse response = ticketService.updateTicket(
            ticketId,
            request,
            agentId,
            Role.AGENT
    );

    // Assert
    assertEquals("IN_PROGRESS", response.status());
    assertEquals("Issue investigated by support.", response.description());

    verify(ticketRepository).save(ticket);
    verify(auditLogService).recordAction(
            eq(ticket),
            eq(agent),
            eq("STATUS_CHANGED"),
            eq("OPEN"),
            eq("IN_PROGRESS")
    );
}


@Test
void customerShouldGetOnlyTheirOwnTickets() {
    // Arrange
    Long customerId = 10L;

    User customer = mock(User.class);
    when(customer.getId()).thenReturn(customerId);

    Ticket ticket = new Ticket(
            "My ticket",
            "I need technical support.",
            "OPEN"
    );
    ticket.setCreatedBy(customer);

    Pageable pageable = PageRequest.of(
            0,
            10,
            Sort.by(Sort.Direction.ASC, "createdAt")
    );

    when(ticketRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(Pageable.class)
    )).thenReturn(new PageImpl<>(List.of(ticket), pageable, 1));

    // Act
    PageResponse<TicketResponse> response = ticketService.getTickets(
            0,
            10,
            "createdAt",
            "asc",
            null,
            null,
            null,
            null,
            null,
            customerId,
            Role.CUSTOMER
    );

    // Assert
    assertEquals(1, response.content().size());
    assertEquals("My ticket", response.content().get(0).title());
    assertEquals(customerId, response.content().get(0).createdBy());

    verify(ticketRepository).findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(Pageable.class)
    );
}

}
