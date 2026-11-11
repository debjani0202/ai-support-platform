package com.supportai.audit.service;

import com.supportai.audit.dto.TicketAuditLogResponse;
import com.supportai.audit.entity.TicketAuditLog;
import com.supportai.audit.repository.TicketAuditLogRepository;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

        Page<TicketAuditLog> page = new PageImpl<>(List.of(logEntry), PageRequest.of(0, 20), 1);

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<TicketAuditLogResponse> history =
                auditLogService.getHistory(ticketId, adminId, Role.ADMIN, 0, 20);

        assertNotNull(history);
        assertEquals(1, history.content().size());
        TicketAuditLogResponse response = history.content().get(0);
        assertEquals(100L, response.id());
        assertEquals(ticketId, response.ticketId());
        assertEquals(10L, response.actorId());
        assertEquals("Alice Customer", response.actorName());
        assertEquals("CREATED", response.action());

        verify(auditLogRepository).findByTicketId(eq(ticketId), any(Pageable.class));
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

        Page<TicketAuditLog> page = new PageImpl<>(List.of(logEntry), PageRequest.of(0, 20), 1);

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<TicketAuditLogResponse> history =
                auditLogService.getHistory(ticketId, customerId, Role.CUSTOMER, 0, 20);

        assertNotNull(history);
        assertEquals(1, history.content().size());
        assertEquals(101L, history.content().get(0).id());
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
                () -> auditLogService.getHistory(ticketId, requestingCustomerId, Role.CUSTOMER, 0, 20)
        );
        assertEquals("You are not allowed to access this ticket's audit history", ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
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

        Page<TicketAuditLog> page = new PageImpl<>(List.of(logEntry), PageRequest.of(0, 20), 1);

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<TicketAuditLogResponse> history =
                auditLogService.getHistory(ticketId, agentId, Role.AGENT, 0, 20);

        assertNotNull(history);
        assertEquals(1, history.content().size());
        assertEquals("STATUS_CHANGED", history.content().get(0).action());
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
                () -> auditLogService.getHistory(ticketId, agentId, Role.AGENT, 0, 20)
        );
        assertEquals("You are not allowed to access this ticket's audit history", ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
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
                () -> auditLogService.getHistory(ticketId, requestingAgentId, Role.AGENT, 0, 20)
        );
        assertEquals("You are not allowed to access this ticket's audit history", ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
    }

    @Test
    void getHistory_missingTicket_throwsResourceNotFoundException() {
        Long ticketId = 999L;

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> auditLogService.getHistory(ticketId, 1L, Role.ADMIN, 0, 20)
        );
        assertEquals("Ticket not found with ID: " + ticketId, ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
    }

    @Test
    void getHistory_ordersChronologicallyByCreatedAtAscThenIdAsc() {
        Long ticketId = 1L;
        Long adminId = 99L;

        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        auditLogService.getHistory(ticketId, adminId, Role.ADMIN, 0, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findByTicketId(eq(ticketId), pageableCaptor.capture());

        Sort capturedSort = pageableCaptor.getValue().getSort();
        Sort.Order createdAtOrder = capturedSort.getOrderFor("createdAt");
        Sort.Order idOrder = capturedSort.getOrderFor("id");

        assertNotNull(createdAtOrder);
        assertTrue(createdAtOrder.isAscending());

        assertNotNull(idOrder);
        assertTrue(idOrder.isAscending());
    }

    @Test
    void recordAction_withExplicitTimestamp_savesAuditRecordWithExactTimestamp() {
        Ticket ticket = mock(Ticket.class);
        User actor = mock(User.class);
        LocalDateTime explicitTime = LocalDateTime.of(2026, 10, 30, 12, 0, 0);

        when(auditLogRepository.save(any(TicketAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TicketAuditLog result = auditLogService.recordAction(
                ticket, actor, "DELETED", "OPEN", "DELETED", explicitTime
        );

        assertNotNull(result);
        assertEquals("DELETED", result.getAction());
        assertEquals(explicitTime, result.getCreatedAt());

        ArgumentCaptor<TicketAuditLog> captor = ArgumentCaptor.forClass(TicketAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(explicitTime, captor.getValue().getCreatedAt());
    }

    @Test
    void getHistory_adminCanRetrieveHistoryForSoftDeletedTicket() {
        Long ticketId = 1L;
        Long adminId = 99L;

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getDeletedAt()).thenReturn(LocalDateTime.now().minusHours(1));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketAuditLog deletedLog = mock(TicketAuditLog.class);
        when(deletedLog.getId()).thenReturn(200L);
        when(deletedLog.getTicket()).thenReturn(ticket);
        User admin = mock(User.class);
        when(admin.getId()).thenReturn(adminId);
        when(admin.getName()).thenReturn("Admin User");
        when(deletedLog.getActor()).thenReturn(admin);
        when(deletedLog.getAction()).thenReturn("DELETED");
        when(deletedLog.getOldValue()).thenReturn("OPEN");
        when(deletedLog.getNewValue()).thenReturn("DELETED");
        when(deletedLog.getCreatedAt()).thenReturn(LocalDateTime.now());

        Page<TicketAuditLog> page = new PageImpl<>(List.of(deletedLog), PageRequest.of(0, 20), 1);

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<TicketAuditLogResponse> history =
                auditLogService.getHistory(ticketId, adminId, Role.ADMIN, 0, 20);

        assertNotNull(history);
        assertEquals(1, history.content().size());
        assertEquals("DELETED", history.content().get(0).action());
    }

    @Test
    void getHistory_customerDeniedAccessToSoftDeletedTicket_throwsResourceNotFoundException() {
        Long ticketId = 1L;
        Long customerId = 10L;

        User customer = mock(User.class);
        when(customer.getId()).thenReturn(customerId);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getCreatedBy()).thenReturn(customer);
        when(ticket.getDeletedAt()).thenReturn(LocalDateTime.now().minusHours(1));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> auditLogService.getHistory(ticketId, customerId, Role.CUSTOMER, 0, 20)
        );
        assertEquals("Ticket not found with ID: " + ticketId, ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
    }

    @Test
    void getHistory_customerDeniedAccessToAnotherCustomerSoftDeletedTicket() {
        Long ticketId = 1L;
        Long ownerId = 10L;
        Long requesterId = 20L;

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getCreatedBy()).thenReturn(owner);
        when(ticket.getDeletedAt()).thenReturn(LocalDateTime.now().minusHours(1));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> auditLogService.getHistory(ticketId, requesterId, Role.CUSTOMER, 0, 20)
        );
        assertEquals("Ticket not found with ID: " + ticketId, ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
    }

    @Test
    void getHistory_agentDeniedAccessToAssignedSoftDeletedTicket_throwsResourceNotFoundException() {
        Long ticketId = 1L;
        Long agentId = 30L;

        User agent = mock(User.class);
        when(agent.getId()).thenReturn(agentId);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getAssignedTo()).thenReturn(agent);
        when(ticket.getDeletedAt()).thenReturn(LocalDateTime.now().minusHours(1));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> auditLogService.getHistory(ticketId, agentId, Role.AGENT, 0, 20)
        );
        assertEquals("Ticket not found with ID: " + ticketId, ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
    }

    @Test
    void getHistory_agentDeniedAccessToUnassignedSoftDeletedTicket() {
        Long ticketId = 1L;
        Long requestingAgentId = 40L;

        Ticket ticket = mock(Ticket.class);
        when(ticket.getAssignedTo()).thenReturn(null);
        when(ticket.getDeletedAt()).thenReturn(LocalDateTime.now().minusHours(1));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> auditLogService.getHistory(ticketId, requestingAgentId, Role.AGENT, 0, 20)
        );
        assertEquals("Ticket not found with ID: " + ticketId, ex.getMessage());
        verify(auditLogRepository, never()).findByTicketId(anyLong(), any(Pageable.class));
    }

    @Test
    void getHistory_firstPage_returnsRequestedElementsAndMetadata() {
        Long ticketId = 1L;
        Long adminId = 99L;

        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        User actor = mock(User.class);
        when(actor.getId()).thenReturn(10L);
        when(actor.getName()).thenReturn("Alice Customer");

        TicketAuditLog log1 = mock(TicketAuditLog.class);
        when(log1.getId()).thenReturn(101L);
        when(log1.getTicket()).thenReturn(ticket);
        when(log1.getActor()).thenReturn(actor);
        when(log1.getAction()).thenReturn("CREATED");
        when(log1.getCreatedAt()).thenReturn(LocalDateTime.now().minusHours(2));

        TicketAuditLog log2 = mock(TicketAuditLog.class);
        when(log2.getId()).thenReturn(102L);
        when(log2.getTicket()).thenReturn(ticket);
        when(log2.getActor()).thenReturn(actor);
        when(log2.getAction()).thenReturn("STATUS_CHANGED");
        when(log2.getCreatedAt()).thenReturn(LocalDateTime.now().minusHours(1));

        Page<TicketAuditLog> firstPage = new PageImpl<>(
                List.of(log1, log2),
                PageRequest.of(0, 2),
                5
        );

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(firstPage);

        PageResponse<TicketAuditLogResponse> response =
                auditLogService.getHistory(ticketId, adminId, Role.ADMIN, 0, 2);

        assertEquals(2, response.content().size());
        assertEquals(101L, response.content().get(0).id());
        assertEquals(102L, response.content().get(1).id());
        assertEquals(0, response.page());
        assertEquals(2, response.size());
        assertEquals(5L, response.totalElements());
        assertEquals(3, response.totalPages());
        assertTrue(response.first());
        assertFalse(response.last());
    }

    @Test
    void getHistory_secondPage_returnsNextElementsAndMetadata() {
        Long ticketId = 1L;
        Long adminId = 99L;

        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        User actor = mock(User.class);
        when(actor.getId()).thenReturn(10L);
        when(actor.getName()).thenReturn("Alice Customer");

        TicketAuditLog log3 = mock(TicketAuditLog.class);
        when(log3.getId()).thenReturn(103L);
        when(log3.getTicket()).thenReturn(ticket);
        when(log3.getActor()).thenReturn(actor);
        when(log3.getAction()).thenReturn("ASSIGNED");
        when(log3.getCreatedAt()).thenReturn(LocalDateTime.now().minusMinutes(30));

        TicketAuditLog log4 = mock(TicketAuditLog.class);
        when(log4.getId()).thenReturn(104L);
        when(log4.getTicket()).thenReturn(ticket);
        when(log4.getActor()).thenReturn(actor);
        when(log4.getAction()).thenReturn("PRIORITY_CHANGED");
        when(log4.getCreatedAt()).thenReturn(LocalDateTime.now().minusMinutes(10));

        Page<TicketAuditLog> secondPage = new PageImpl<>(
                List.of(log3, log4),
                PageRequest.of(1, 2),
                5
        );

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(secondPage);

        PageResponse<TicketAuditLogResponse> response =
                auditLogService.getHistory(ticketId, adminId, Role.ADMIN, 1, 2);

        assertEquals(2, response.content().size());
        assertEquals(103L, response.content().get(0).id());
        assertEquals(104L, response.content().get(1).id());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5L, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }

    @Test
    void getHistory_emptyResult_returnsEmptyPageMetadata() {
        Long ticketId = 1L;
        Long adminId = 99L;

        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        Page<TicketAuditLog> emptyPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        );

        when(auditLogRepository.findByTicketId(eq(ticketId), any(Pageable.class)))
                .thenReturn(emptyPage);

        PageResponse<TicketAuditLogResponse> response =
                auditLogService.getHistory(ticketId, adminId, Role.ADMIN, 0, 20);

        assertTrue(response.content().isEmpty());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(0L, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.first());
        assertTrue(response.last());
    }

    @Test
    void getHistory_negativePage_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> auditLogService.getHistory(1L, 1L, Role.ADMIN, -1, 20)
        );

        assertEquals("Page number cannot be negative", ex.getMessage());
        verifyNoInteractions(ticketRepository);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void getHistory_zeroSize_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> auditLogService.getHistory(1L, 1L, Role.ADMIN, 0, 0)
        );

        assertEquals("Page size must be between 1 and 100", ex.getMessage());
        verifyNoInteractions(ticketRepository);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void getHistory_oversizedPage_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> auditLogService.getHistory(1L, 1L, Role.ADMIN, 0, 101)
        );

        assertEquals("Page size must be between 1 and 100", ex.getMessage());
        verifyNoInteractions(ticketRepository);
        verifyNoInteractions(auditLogRepository);
    }
}
