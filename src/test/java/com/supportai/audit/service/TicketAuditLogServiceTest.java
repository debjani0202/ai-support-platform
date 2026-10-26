package com.supportai.audit.service;

import com.supportai.audit.dto.TicketAuditLogResponse;
import com.supportai.audit.entity.TicketAuditLog;
import com.supportai.audit.repository.TicketAuditLogRepository;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TicketAuditLogServiceTest {

    private TicketAuditLogRepository auditLogRepository;
    private TicketRepository ticketRepository;
    private TicketAuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(TicketAuditLogRepository.class);
        ticketRepository = mock(TicketRepository.class);
        auditLogService = new TicketAuditLogService(auditLogRepository, ticketRepository);
    }

    @Test
    void recordAction_savesAuditRecordWithCorrectDetails() {
        Ticket ticket = mock(Ticket.class);
        User actor = mock(User.class);
        String action = "TITLE_CHANGED";
        String oldValue = "Old Title";
        String newValue = "New Title";

        when(auditLogRepository.save(any(TicketAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TicketAuditLog result = auditLogService.recordAction(ticket, actor, action, oldValue, newValue);

        assertNotNull(result);
        assertEquals(ticket, result.getTicket());
        assertEquals(actor, result.getActor());
        assertEquals(action, result.getAction());
        assertEquals(oldValue, result.getOldValue());
        assertEquals(newValue, result.getNewValue());

        ArgumentCaptor<TicketAuditLog> captor = ArgumentCaptor.forClass(TicketAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        TicketAuditLog saved = captor.getValue();
        assertEquals(ticket, saved.getTicket());
        assertEquals(actor, saved.getActor());
        assertEquals(action, saved.getAction());
        assertEquals(oldValue, saved.getOldValue());
        assertEquals(newValue, saved.getNewValue());
    }

    @Test
    void getHistory_adminCanRetrieveAnyTicketHistory() {
        Long ticketId = 1L;
        Long adminId = 99L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(10L);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getCreatedBy()).thenReturn(customer);
        when(ticket.getAssignedTo()).thenReturn(null);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketAuditLog logEntry = mock(TicketAuditLog.class);
        when(logEntry.getId()).thenReturn(100L);
        when(logEntry.getTicket()).thenReturn(ticket);
        when(logEntry.getActor()).thenReturn(customer);
        when(customer.getName()).thenReturn("Alice Customer");
        when(logEntry.getAction()).thenReturn("CREATED");
        when(logEntry.getOldValue()).thenReturn(null);
        when(logEntry.getNewValue()).thenReturn("Ticket created: Issue");
        when(logEntry.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Sort.class)))
                .thenReturn(List.of(logEntry));

        List<TicketAuditLogResponse> history = auditLogService.getHistory(ticketId, adminId, Role.ADMIN);

        assertNotNull(history);
        assertEquals(1, history.size());
        TicketAuditLogResponse response = history.get(0);
        assertEquals(100L, response.id());
        assertEquals(ticketId, response.ticketId());
        assertEquals(10L, response.actorId());
        assertEquals("Alice Customer", response.actorName());
        assertEquals("CREATED", response.action());

        verify(auditLogRepository).findByTicketId(eq(ticketId), any(Sort.class));
    }

    @Test
    void getHistory_customerCanRetrieveOwnTicketHistory() {
        Long ticketId = 1L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);
        when(customer.getName()).thenReturn("Alice Customer");

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getCreatedBy()).thenReturn(customer);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketAuditLog logEntry = mock(TicketAuditLog.class);
        when(logEntry.getId()).thenReturn(101L);
        when(logEntry.getTicket()).thenReturn(ticket);
        when(logEntry.getActor()).thenReturn(customer);
        when(logEntry.getAction()).thenReturn("CREATED");
        when(logEntry.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Sort.class)))
                .thenReturn(List.of(logEntry));

        List<TicketAuditLogResponse> history = auditLogService.getHistory(ticketId, customerId, Role.CUSTOMER);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(101L, history.get(0).id());
    }

    @Test
    void getHistory_customerDeniedAccessToAnotherCustomerHistory_throwsForbiddenException() {
        Long ticketId = 1L;
        Long ownerCustomerId = 10L;
        Long requestingCustomerId = 20L;

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerCustomerId);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getCreatedBy()).thenReturn(owner);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> auditLogService.getHistory(ticketId, requestingCustomerId, Role.CUSTOMER)
        );
        assertEquals("You are not allowed to access this ticket's audit history", ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void getHistory_agentCanRetrieveAssignedTicketHistory() {
        Long ticketId = 1L;
        Long agentId = 30L;

        User agent = mock(User.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getName()).thenReturn("Bob Agent");

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getAssignedTo()).thenReturn(agent);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketAuditLog logEntry = mock(TicketAuditLog.class);
        when(logEntry.getId()).thenReturn(102L);
        when(logEntry.getTicket()).thenReturn(ticket);
        when(logEntry.getActor()).thenReturn(agent);
        when(logEntry.getAction()).thenReturn("STATUS_CHANGED");
        when(logEntry.getOldValue()).thenReturn("OPEN");
        when(logEntry.getNewValue()).thenReturn("IN_PROGRESS");
        when(logEntry.getCreatedAt()).thenReturn(LocalDateTime.now());

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Sort.class)))
                .thenReturn(List.of(logEntry));

        List<TicketAuditLogResponse> history = auditLogService.getHistory(ticketId, agentId, Role.AGENT);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("STATUS_CHANGED", history.get(0).action());
    }

    @Test
    void getHistory_agentDeniedAccessToUnassignedTicket_throwsForbiddenException() {
        Long ticketId = 1L;
        Long agentId = 30L;

        Ticket ticket = mock(Ticket.class);
        when(ticket.getAssignedTo()).thenReturn(null);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> auditLogService.getHistory(ticketId, agentId, Role.AGENT)
        );
        assertEquals("You are not allowed to access this ticket's audit history", ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void getHistory_agentDeniedAccessToTicketAssignedToAnotherAgent_throwsForbiddenException() {
        Long ticketId = 1L;
        Long assignedAgentId = 30L;
        Long requestingAgentId = 31L;

        User assignedAgent = mock(User.class);
        when(assignedAgent.getId()).thenReturn(assignedAgentId);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getAssignedTo()).thenReturn(assignedAgent);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> auditLogService.getHistory(ticketId, requestingAgentId, Role.AGENT)
        );
        assertEquals("You are not allowed to access this ticket's audit history", ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void getHistory_missingTicket_throwsResourceNotFoundException() {
        Long ticketId = 999L;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> auditLogService.getHistory(ticketId, 1L, Role.ADMIN)
        );
        assertEquals("Ticket not found with ID: " + ticketId, ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(any(), any());
    }

    @Test
    void getHistory_ordersChronologicallyByCreatedAtAscThenIdAsc() {
        Long ticketId = 1L;
        Long adminId = 99L;

        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(auditLogRepository.findByTicketId(eq(ticketId), any(Sort.class))).thenReturn(List.of());

        auditLogService.getHistory(ticketId, adminId, Role.ADMIN);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(auditLogRepository).findByTicketId(eq(ticketId), sortCaptor.capture());

        Sort capturedSort = sortCaptor.getValue();
        Sort.Order createdAtOrder = capturedSort.getOrderFor("createdAt");
        Sort.Order idOrder = capturedSort.getOrderFor("id");

        assertNotNull(createdAtOrder);
        assertTrue(createdAtOrder.isAscending());

        assertNotNull(idOrder);
        assertTrue(idOrder.isAscending());
    }
}
