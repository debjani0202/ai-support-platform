
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles recording and retrieving ticket audit history.
 *
 * The service is responsible for:
 * 1. Saving audit events.
 * 2. Checking whether a user can view a ticket's history.
 * 3. Returning audit records in chronological order.
 */
@Service
@Transactional
public class TicketAuditLogService {

    private final TicketAuditLogRepository auditLogRepository;
    private final TicketRepository ticketRepository;

    /**
     * Constructor injection allows Spring to provide the repositories.
     */
    public TicketAuditLogService(
            TicketAuditLogRepository auditLogRepository,
            TicketRepository ticketRepository) {

        this.auditLogRepository = auditLogRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Records an action performed on a ticket.
     *
     * @param ticket the ticket affected by the action
     * @param actor the user who performed the action
     * @param action the action type, such as CREATED
     * @param oldValue the previous value, if applicable
     * @param newValue the new value, if applicable
     * @return the saved audit record
     */
    public TicketAuditLog recordAction(
            Ticket ticket,
            User actor,
            String action,
            String oldValue,
            String newValue) {
        return recordAction(ticket, actor, action, oldValue, newValue, null);
    }

    /**
     * Records an action performed on a ticket with an explicit timestamp.
     *
     * @param ticket the ticket affected by the action
     * @param actor the user who performed the action
     * @param action the action type, such as DELETED
     * @param oldValue the previous value, if applicable
     * @param newValue the new value, if applicable
     * @param timestamp the explicit timestamp to record
     * @return the saved audit record
     */
    public TicketAuditLog recordAction(
            Ticket ticket,
            User actor,
            String action,
            String oldValue,
            String newValue,
            LocalDateTime timestamp) {

        TicketAuditLog auditLog = timestamp != null
                ? new TicketAuditLog(
                        ticket,
                        actor,
                        action,
                        oldValue,
                        newValue,
                        timestamp
                )
                : new TicketAuditLog(
                        ticket,
                        actor,
                        action,
                        oldValue,
                        newValue
                );

        return auditLogRepository.save(auditLog);
    }

    /**
     * Retrieves audit history only if the authenticated user
     * is authorized to access the requested ticket.
     */
    @Transactional(readOnly = true)
    public List<TicketAuditLogResponse> getHistory(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole) {

        // Find the requested ticket or return a 404 response
        // through the application's ResourceNotFoundException handler.
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Ticket not found with ID: " + ticketId
                        )
                );

        boolean allowed;

        // Administrators can view every ticket's audit history.
        if (currentUserRole == Role.ADMIN) {
            allowed = true;

        // Customers can view only tickets they created.
        } else if (currentUserRole == Role.CUSTOMER) {
            allowed = ticket.getCreatedBy() != null
                    && ticket.getCreatedBy().getId().equals(currentUserId);

        // Agents can view only tickets assigned to them.
        } else if (currentUserRole == Role.AGENT) {
            allowed = ticket.getAssignedTo() != null
                    && ticket.getAssignedTo().getId().equals(currentUserId);

        } else {
            allowed = false;
        }

        // Never disclose audit history when access is denied.
        if (!allowed) {
            throw new ForbiddenException(
                    "You are not allowed to access this ticket's audit history"
            );
        }

        // Sort oldest first. The ID provides deterministic ordering
        // when multiple records have the same timestamp.
        Sort chronologicalOrder = Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")
        );

        // Convert database entities into DTOs before returning them.
        return auditLogRepository.findByTicketId(
                        ticketId,
                        chronologicalOrder
                )
                .stream()
                .map(TicketAuditLogResponse::from)
                .toList();
    }
}
