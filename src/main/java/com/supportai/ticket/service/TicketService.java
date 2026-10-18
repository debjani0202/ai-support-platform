
package com.supportai.ticket.service;

import com.supportai.ai.TicketCategoryClassifier;

import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.notification.service.NotificationService;
import com.supportai.ticket.dto.CreateTicketRequest;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.dto.UpdateTicketRequest;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.ticket.repository.TicketSpecifications;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * Contains the business rules that determine what an authenticated user
 * can do with a ticket.
 *
 * Ticket changes and their audit events participate in the same transaction.
 */
@Service
@Transactional
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketAuditLogService auditLogService;
    private final NotificationService notificationService;
    private final TicketCategoryClassifier categoryClassifier;

    /**
     * Constructor injection lets Spring provide all required dependencies.
     */
    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            TicketAuditLogService auditLogService,
            NotificationService notificationService,
            TicketCategoryClassifier categoryClassifier
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.categoryClassifier = categoryClassifier;
    }

    /**
     * Creates a ticket and records a CREATED audit event.
     */
    public TicketResponse createTicket(
            CreateTicketRequest request,
            Long currentUserId
    ) {
        User currentUser = findUserById(currentUserId);

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        TicketCategory category = request.category();

        if (category == null) {
            category = categoryClassifier.classify(
                    request.title(),
                    request.description()
            );
        }

        ticket.setCategory(category);
        ticket.setPriority(request.priority());
        ticket.setCreatedBy(currentUser);

        Ticket savedTicket = ticketRepository.save(ticket);

        // Record who created the ticket and what was created.
        auditLogService.recordAction(
                savedTicket,
                currentUser,
                "CREATED",
                null,
                "Ticket created: " + savedTicket.getTitle()
        );

        return mapToResponse(savedTicket);
    }

    /**
     * Retrieves a paginated and filtered ticket list.
     *
     * Customers see their own tickets, agents see their assigned tickets,
     * and administrators can see all tickets.
     */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> getTickets(
            int page,
            int size,
            String sortBy,
            String direction,
            String status,
            String search,
            TicketPriority priority,
            TicketCategory category,
            Long assignedAgentId,
            Long currentUserId,
            Role currentUserRole
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        if (!"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException(
                    "Direction must be asc or desc"
            );
        }

        // Allow only known entity fields for sorting.
        if (sortBy == null || !Set.of(
                "id",
                "title",
                "status",
                "priority",
                "category",
                "createdAt",
                "updatedAt"
        ).contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field"
            );
        }

        // Only administrators may select another agent's tickets.
        if (assignedAgentId != null
                && currentUserRole != Role.ADMIN) {
            throw new ForbiddenException(
                    "Only administrators can filter by assigned agent"
            );
        }

        if (assignedAgentId != null && assignedAgentId < 1) {
            throw new IllegalArgumentException(
                    "Assigned agent ID must be positive"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.fromString(direction),
                        sortBy
                )
        );

        // Build filters dynamically so any combination can be used.
        Specification<Ticket> specification =
                TicketSpecifications.hasStatus(status)
                        .and(TicketSpecifications.hasPriority(priority))
                        .and(TicketSpecifications.hasCategory(category))
                        .and(TicketSpecifications.matchesSearch(search));

        // Enforce visibility in the database query itself.
        if (currentUserRole == Role.CUSTOMER) {
            specification = specification.and(
                    TicketSpecifications.createdBy(currentUserId)
            );
        } else if (currentUserRole == Role.AGENT) {
            specification = specification.and(
                    TicketSpecifications.assignedTo(currentUserId)
            );
        } else if (currentUserRole == Role.ADMIN) {
            specification = specification.and(
                    TicketSpecifications.assignedToIfProvided(
                            assignedAgentId
                    )
            );
        } else {
            throw new ForbiddenException(
                    "You are not allowed to access tickets"
            );
        }

        Page<Ticket> ticketPage =
                ticketRepository.findAll(specification, pageable);

        return new PageResponse<>(
                ticketPage.getContent()
                        .stream()
                        .map(this::mapToResponse)
                        .toList(),
                ticketPage.getNumber(),
                ticketPage.getSize(),
                ticketPage.getTotalElements(),
                ticketPage.getTotalPages(),
                ticketPage.isFirst(),
                ticketPage.isLast()
        );
    }
    /**
     * Retrieves a ticket after checking the user's access permissions.
     */
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole
    ) {
        Ticket ticket = findTicketById(ticketId);

        validateTicketAccess(
                ticket,
                currentUserId,
                currentUserRole
        );

        return mapToResponse(ticket);
    }

    /**
     * Updates ticket information and records actual status changes.
     */
    public TicketResponse updateTicket(
            Long ticketId,
            UpdateTicketRequest request,
            Long currentUserId,
            Role currentUserRole
    ) {
        Ticket ticket = findTicketById(ticketId);

        validateTicketAccess(
                ticket,
                currentUserId,
                currentUserRole
        );

        // Save the original status before changing the ticket.
        String oldStatus = ticket.getStatus();

        // Customers may edit ticket details but cannot change status.
        if (currentUserRole == Role.CUSTOMER
                && !Objects.equals(ticket.getStatus(), request.status())) {
            throw new ForbiddenException(
                    "Customers cannot change ticket status"
            );
        }

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        TicketCategory category = request.category();

        if (category == null) {
            category = categoryClassifier.classify(
                    request.title(),
                    request.description()
            );
        }

        ticket.setCategory(category);
        ticket.setPriority(request.priority());

        // Only agents and administrators can change ticket status.
        if (currentUserRole == Role.AGENT
                || currentUserRole == Role.ADMIN) {
            validateStatusTransition(oldStatus, request.status());
            ticket.setStatus(request.status());
        }

        Ticket updatedTicket = ticketRepository.save(ticket);

        // Create an audit event only if the status actually changed.
        if (!Objects.equals(oldStatus, updatedTicket.getStatus())) {
            User actor = findUserById(currentUserId);

            auditLogService.recordAction(
                    updatedTicket,
                    actor,
                    "STATUS_CHANGED",
                    oldStatus,
                    updatedTicket.getStatus()
            );
            Long creatorId = updatedTicket.getCreatedBy().getId();

            User assignedAgent = updatedTicket.getAssignedTo();
            Long assignedAgentId = assignedAgent != null
                    ? assignedAgent.getId()
                    : null;

            String notificationMessage =
                    "Ticket #" + updatedTicket.getId()
                            + " status changed from "
                            + oldStatus + " to "
                            + updatedTicket.getStatus()
                            + ": " + updatedTicket.getTitle();

            // Notify the creator unless they made the change.
            if (!Objects.equals(creatorId, currentUserId)) {
                notificationService.createNotification(
                        updatedTicket.getCreatedBy(),
                        updatedTicket,
                        "TICKET_UPDATED",
                        notificationMessage
                );
            }

            // Notify the assigned agent without duplicates.
            if (assignedAgent != null
                    && !Objects.equals(assignedAgentId, currentUserId)
                    && !Objects.equals(assignedAgentId, creatorId)) {
                notificationService.createNotification(
                        assignedAgent,
                        updatedTicket,
                        "TICKET_UPDATED",
                        notificationMessage
                );
            }
        }

        return mapToResponse(updatedTicket);
    }

    /**
     * Assigns a ticket to an agent.
     * Only administrators can perform this operation.
     */
    public TicketResponse assignTicket(
            Long ticketId,
            Long agentId,
            Long currentUserId,
            Role currentUserRole
    ) {
        if (currentUserRole != Role.ADMIN) {
            throw new ForbiddenException(
                    "Only administrators can assign tickets"
            );
        }

        Ticket ticket = findTicketById(ticketId);
        User agent = findUserById(agentId);

        if (agent.getRole() != Role.AGENT) {
            throw new IllegalArgumentException(
                    "The selected user is not an agent"
            );
        }

        // Remember the previous assignee before updating the ticket.
        Long oldAgentId = ticket.getAssignedTo() != null
                ? ticket.getAssignedTo().getId()
                : null;

        ticket.setAssignedTo(agent);

        Ticket updatedTicket = ticketRepository.save(ticket);

        // Avoid creating duplicate events when the assignee is unchanged.
        if (!Objects.equals(oldAgentId, agent.getId())) {
            User actor = findUserById(currentUserId);

            auditLogService.recordAction(
                    updatedTicket,
                    actor,
                    "ASSIGNED",
                    oldAgentId != null ? oldAgentId.toString() : null,
                    agent.getId().toString()
            );

            notificationService.createNotification(
                    agent,
                    updatedTicket,
                    "TICKET_ASSIGNED",
                    "Ticket #" + updatedTicket.getId()
                            + " has been assigned to you: "
                            + updatedTicket.getTitle()
            );
        }

        return mapToResponse(updatedTicket);
    }

    /**
     * Deletes a ticket only when the existing access rules permit it.
     */
    public void deleteTicket(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole
    ) {
        Ticket ticket = findTicketById(ticketId);

        validateTicketAccess(
                ticket,
                currentUserId,
                currentUserRole
        );

        // Customers cannot delete tickets.
        if (currentUserRole == Role.CUSTOMER) {
            throw new ForbiddenException(
                    "Customers cannot delete tickets"
            );
        }

        ticketRepository.delete(ticket);
    }

    /**
     * Finds a ticket or throws a not-found exception.
     */
    private Ticket findTicketById(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Ticket not found with ID: " + ticketId
                        )
                );
    }

    /**
     * Finds a user or throws a not-found exception.
     */
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with ID: " + userId
                        )
                );
    }

    /**
     * Enforces the existing role-based ticket access rules.
     */
    private void validateTicketAccess(
            Ticket ticket,
            Long currentUserId,
            Role currentUserRole
    ) {
        if (currentUserRole == Role.ADMIN) {
            return;
        }

        if (currentUserRole == Role.CUSTOMER) {
            if (ticket.getCreatedBy() == null
                    || !ticket.getCreatedBy().getId()
                            .equals(currentUserId)) {
                throw new ForbiddenException(
                        "You are not allowed to access this ticket"
                );
            }

            return;
        }

        if (currentUserRole == Role.AGENT) {
            if (ticket.getAssignedTo() == null
                    || !ticket.getAssignedTo().getId()
                            .equals(currentUserId)) {
                throw new ForbiddenException(
                        "You are not allowed to access this ticket"
                );
            }

            return;
        }

        throw new ForbiddenException(
                "You are not allowed to access this ticket"
        );
    }

    /**
     * Converts the Ticket entity into the API response DTO.
     */
    private TicketResponse mapToResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getCreatedBy().getId(),
                ticket.getAssignedTo() != null
                        ? ticket.getAssignedTo().getId()
                        : null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }


private void validateStatusTransition(String currentStatus, String nextStatus) {
    if (Objects.equals(currentStatus, nextStatus)) {
        return;
    }

    boolean allowed = switch (currentStatus) {
        case "OPEN" -> "IN_PROGRESS".equals(nextStatus);
        case "IN_PROGRESS" -> "RESOLVED".equals(nextStatus);
        case "RESOLVED" -> "CLOSED".equals(nextStatus);
        case "CLOSED" -> false;
        default -> false;
    };

    if (!allowed) {
        throw new IllegalArgumentException(
                "Invalid ticket status transition: "
                        + currentStatus + " -> " + nextStatus
        );
    }
}


}
