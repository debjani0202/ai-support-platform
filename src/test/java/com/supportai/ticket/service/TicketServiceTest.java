
package com.supportai.ticket.service;

import com.supportai.ai.TicketCategoryClassifier;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TicketServiceTest {

    private TicketRepository ticketRepository;
    private UserRepository userRepository;
    private TicketAuditLogService auditLogService;
    private NotificationService notificationService;
    private TicketCategoryClassifier categoryClassifier;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogService = mock(TicketAuditLogService.class);
        notificationService = mock(NotificationService.class);
        categoryClassifier = mock(TicketCategoryClassifier.class);

        ticketService = new TicketService(
                ticketRepository,
                userRepository,
                auditLogService,
                notificationService,
                categoryClassifier);
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
    void createTicketShouldClassifyCategoryWhenCategoryIsMissing() {
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);
        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(categoryClassifier.classify(
                anyString(), anyString()))
                .thenReturn(TicketCategory.ACCOUNT_ACCESS);

        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest(
                "Cannot log in",
                "I cannot access my account.",
                null,
                TicketPriority.HIGH
        );

        TicketResponse response =
                ticketService.createTicket(request, customerId);

        assertEquals(TicketCategory.ACCOUNT_ACCESS, response.category());

        verify(categoryClassifier).classify(
                "Cannot log in",
                "I cannot access my account."
        );
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void createTicketShouldNotClassifyCategoryWhenCategoryIsProvided() {
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);
        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest(
                "Cannot log in",
                "I cannot access my account.",
                TicketCategory.ACCOUNT_ACCESS,
                TicketPriority.HIGH
        );

        TicketResponse response =
                ticketService.createTicket(request, customerId);

        assertEquals(TicketCategory.ACCOUNT_ACCESS, response.category());
        verifyNoInteractions(categoryClassifier);
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


    @Test
    void updateTicketShouldPreserveNullCategoryAndNotCallClassifier() {
        // Arrange
        Long ticketId = 101L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket(
                "Old Title",
                "Old Description",
                "OPEN"
        );
        ticket.setCategory(null);
        ticket.setPriority(TicketPriority.LOW);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Updated Title",
                "Updated Description",
                "OPEN",
                null,
                TicketPriority.HIGH
        );

        // Act
        TicketResponse response = ticketService.updateTicket(
                ticketId,
                request,
                customerId,
                Role.CUSTOMER
        );

        // Assert
        assertEquals("Updated Title", response.title());
        assertEquals("Updated Description", response.description());
        assertEquals("OPEN", response.status());
        assertNull(response.category(), "Category should remain null when request category is null");
        assertEquals(TicketPriority.HIGH, response.priority());

        verify(ticketRepository).save(ticket);
        verifyNoInteractions(categoryClassifier);
    }

    @Test
    void updateTicketShouldTransitionStatusWhenCategoryIsNull() {
        // Arrange
        Long ticketId = 102L;
        Long agentId = 20L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        User agent = mock(User.class);
        when(agent.getId()).thenReturn(agentId);

        Ticket ticket = new Ticket(
                "Login issue",
                "Unable to log into account",
                "OPEN"
        );
        ticket.setCategory(null);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setCreatedBy(customer);
        ticket.setAssignedTo(agent);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(agentId))
                .thenReturn(Optional.of(agent));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Login issue",
                "Unable to log into account",
                "IN_PROGRESS",
                null,
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
        assertNull(response.category(), "Category should remain null during status transition");
        verify(ticketRepository).save(ticket);
        verify(auditLogService).recordAction(
                eq(ticket),
                eq(agent),
                eq("STATUS_CHANGED"),
                eq("OPEN"),
                eq("IN_PROGRESS")
        );
        verifyNoInteractions(categoryClassifier);
    }

    @Test
    void updateTicketShouldPreserveExistingCategoryWhenRequestCategoryIsNull() {
        // Arrange
        Long ticketId = 103L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket(
                "Payment Failure",
                "Payment did not go through",
                "OPEN"
        );
        ticket.setCategory(TicketCategory.PAYMENT);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Payment Failure - Urgent",
                "Payment did not go through on credit card",
                "OPEN",
                null,
                TicketPriority.URGENT
        );

        // Act
        TicketResponse response = ticketService.updateTicket(
                ticketId,
                request,
                customerId,
                Role.CUSTOMER
        );

        // Assert
        assertEquals("Payment Failure - Urgent", response.title());
        assertEquals("Payment did not go through on credit card", response.description());
        assertEquals(TicketCategory.PAYMENT, response.category(), "Existing category must be preserved when request category is null");
        assertEquals(TicketPriority.URGENT, response.priority());

        verify(ticketRepository).save(ticket);
        verifyNoInteractions(categoryClassifier);
    }

    @Test
    void updateTicketShouldUpdateCategoryWhenExplicitlyProvided() {
        // Arrange
        Long ticketId = 104L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket(
                "App Crash",
                "App crashes on launch",
                "OPEN"
        );
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.LOW);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId))
                .thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId))
                .thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "App Crash",
                "App crashes on launch",
                "OPEN",
                TicketCategory.BUG_REPORT,
                TicketPriority.HIGH
        );

        // Act
        TicketResponse response = ticketService.updateTicket(
                ticketId,
                request,
                customerId,
                Role.CUSTOMER
        );

        // Assert
        assertEquals(TicketCategory.BUG_REPORT, response.category(), "Category should be updated to explicit category provided in request");
        assertEquals(TicketPriority.HIGH, response.priority());

        verify(ticketRepository).save(ticket);
        verifyNoInteractions(categoryClassifier);
    }

    @Test
    void updateTicket_shouldRecordTitleChangedAuditEventWhenTitleChanges() {
        Long ticketId = 201L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket("Original Title", "Same Description", "OPEN");
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "New Title",
                "Same Description",
                "OPEN",
                TicketCategory.GENERAL_QUERY,
                TicketPriority.MEDIUM
        );

        ticketService.updateTicket(ticketId, request, customerId, Role.CUSTOMER);

        verify(auditLogService).recordAction(
                eq(ticket),
                eq(customer),
                eq("TITLE_CHANGED"),
                eq("Original Title"),
                eq("New Title")
        );
        verify(auditLogService, never()).recordAction(any(), any(), eq("DESCRIPTION_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("CATEGORY_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("PRIORITY_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("STATUS_CHANGED"), any(), any());
    }

    @Test
    void updateTicket_shouldRecordDescriptionChangedAuditEventWhenDescriptionChanges() {
        Long ticketId = 202L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket("Same Title", "Original Description", "OPEN");
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Same Title",
                "New Description",
                "OPEN",
                TicketCategory.GENERAL_QUERY,
                TicketPriority.MEDIUM
        );

        ticketService.updateTicket(ticketId, request, customerId, Role.CUSTOMER);

        verify(auditLogService).recordAction(
                eq(ticket),
                eq(customer),
                eq("DESCRIPTION_CHANGED"),
                eq("Original Description"),
                eq("New Description")
        );
        verify(auditLogService, never()).recordAction(any(), any(), eq("TITLE_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("CATEGORY_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("PRIORITY_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("STATUS_CHANGED"), any(), any());
    }

    @Test
    void updateTicket_shouldRecordCategoryChangedAuditEventWhenCategoryChanges() {
        Long ticketId = 203L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket("Same Title", "Same Description", "OPEN");
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Same Title",
                "Same Description",
                "OPEN",
                TicketCategory.PAYMENT,
                TicketPriority.MEDIUM
        );

        ticketService.updateTicket(ticketId, request, customerId, Role.CUSTOMER);

        verify(auditLogService).recordAction(
                eq(ticket),
                eq(customer),
                eq("CATEGORY_CHANGED"),
                eq("GENERAL_QUERY"),
                eq("PAYMENT")
        );
        verify(auditLogService, never()).recordAction(any(), any(), eq("TITLE_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("DESCRIPTION_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("PRIORITY_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("STATUS_CHANGED"), any(), any());
    }

    @Test
    void updateTicket_shouldRecordPriorityChangedAuditEventWhenPriorityChanges() {
        Long ticketId = 204L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket("Same Title", "Same Description", "OPEN");
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.LOW);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Same Title",
                "Same Description",
                "OPEN",
                TicketCategory.GENERAL_QUERY,
                TicketPriority.URGENT
        );

        ticketService.updateTicket(ticketId, request, customerId, Role.CUSTOMER);

        verify(auditLogService).recordAction(
                eq(ticket),
                eq(customer),
                eq("PRIORITY_CHANGED"),
                eq("LOW"),
                eq("URGENT")
        );
        verify(auditLogService, never()).recordAction(any(), any(), eq("TITLE_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("DESCRIPTION_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("CATEGORY_CHANGED"), any(), any());
        verify(auditLogService, never()).recordAction(any(), any(), eq("STATUS_CHANGED"), any(), any());
    }

    @Test
    void updateTicket_withNoFieldOrStatusChanges_recordsNoAuditEvent() {
        Long ticketId = 205L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = new Ticket("Existing Title", "Existing Description", "OPEN");
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "Existing Title",
                "Existing Description",
                "OPEN",
                TicketCategory.GENERAL_QUERY,
                TicketPriority.MEDIUM
        );

        ticketService.updateTicket(ticketId, request, customerId, Role.CUSTOMER);

        verify(ticketRepository).save(ticket);
        verifyNoInteractions(auditLogService);
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void updateTicket_withCombinedFieldAndStatusChanges_recordsAllAuditEvents() {
        Long ticketId = 206L;
        Long agentId = 30L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        User agent = mock(User.class);
        when(agent.getId()).thenReturn(agentId);

        Ticket ticket = new Ticket("Old Title", "Old Description", "OPEN");
        ticket.setCategory(TicketCategory.GENERAL_QUERY);
        ticket.setPriority(TicketPriority.LOW);
        ticket.setCreatedBy(customer);
        ticket.setAssignedTo(agent);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTicketRequest request = new UpdateTicketRequest(
                "New Title",
                "New Description",
                "IN_PROGRESS",
                TicketCategory.BUG_REPORT,
                TicketPriority.HIGH
        );

        ticketService.updateTicket(ticketId, request, agentId, Role.AGENT);

        verify(auditLogService).recordAction(
                eq(ticket),
                eq(agent),
                eq("TITLE_CHANGED"),
                eq("Old Title"),
                eq("New Title")
        );
        verify(auditLogService).recordAction(
                eq(ticket),
                eq(agent),
                eq("DESCRIPTION_CHANGED"),
                eq("Old Description"),
                eq("New Description")
        );
        verify(auditLogService).recordAction(
                eq(ticket),
                eq(agent),
                eq("CATEGORY_CHANGED"),
                eq("GENERAL_QUERY"),
                eq("BUG_REPORT")
        );
        verify(auditLogService).recordAction(
                eq(ticket),
                eq(agent),
                eq("PRIORITY_CHANGED"),
                eq("LOW"),
                eq("HIGH")
        );
        verify(auditLogService).recordAction(
                eq(ticket),
                eq(agent),
                eq("STATUS_CHANGED"),
                eq("OPEN"),
                eq("IN_PROGRESS")
        );
    }
}
