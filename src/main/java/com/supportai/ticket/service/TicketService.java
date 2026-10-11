//The ticket service contains business rules that determine what an authenticated user can actually do with a ticket.
package com.supportai.ticket.service;

import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.dto.CreateTicketRequest;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.dto.UpdateTicketRequest;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional  //transaction groups database operations into a unit of work. If an operation fails, the transaction can be rolled back according to the transaction rules.
public class TicketService {

        //private means the fields can be accessed directly only inside this class.
        //final means each field must be assigned once and cannot later be reassigned.
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    public TicketResponse createTicket(
            CreateTicketRequest request,  //CreateTicketRequest request contains the information submitted for the new ticket, such as its title, description, category and priority.
            Long currentUserId  //Long currentUserId identifies the user creating the ticket. This ID should come from the authenticated request context, not from a customer-controlled field in the request body.
    ) {
        User currentUser = findUserById(currentUserId);

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setCreatedBy(currentUser);

        Ticket savedTicket = ticketRepository.save(ticket);

        return mapToResponse(savedTicket);
    }

    @Transactional(readOnly = true)//->Marks this database operation as read-only.
    public PageResponse<TicketResponse> getTickets(
            int page,  //Control pagination.
            int size,
            String sortBy, //The field used to order tickets, such as createdAt.
            String direction, //asc for ascending or desc for descending.
            String status, //Optional status filter, such as RESOLVED
            String search, //Optional text search against ticket titles.
            Long currentUserId, //Identifies the logged-in user.
            Role currentUserRole //Determines which tickets that user may list.
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

        if (!java.util.Set.of(
                "id", "title", "status",
                "priority", "category",
                "createdAt", "updatedAt"
        ).contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field"
            );
        }

        Sort.Direction sortDirection =
                Sort.Direction.fromString(direction);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        boolean hasStatus =
                status != null && !status.isBlank();

        boolean hasSearch =
                search != null && !search.isBlank();

        String searchText = hasSearch ? search.trim() : "";

        Page<Ticket> ticketPage;

        if (currentUserRole == Role.ADMIN) {

            if (hasStatus && hasSearch) {
                ticketPage = ticketRepository
                        .findByStatusIgnoreCaseAndTitleContainingIgnoreCase(
                                status.trim(),
                                searchText,
                                pageable
                        );
            } else if (hasStatus) {
                ticketPage = ticketRepository
                        .findByStatusIgnoreCase(
                                status.trim(),
                                pageable
                        );
            } else if (hasSearch) {
                ticketPage = ticketRepository
                        .findByTitleContainingIgnoreCase(
                                searchText,
                                pageable
                        );
            } else {
                ticketPage = ticketRepository.findAll(pageable);
            }

        } else if (currentUserRole == Role.CUSTOMER) {

            if (hasStatus && hasSearch) {
                ticketPage = ticketRepository
                        .findByCreatedByIdAndStatusIgnoreCaseAndTitleContainingIgnoreCase(
                                currentUserId,
                                status.trim(),
                                searchText,
                                pageable
                        );
            } else if (hasStatus) {
                ticketPage = ticketRepository
                        .findByCreatedByIdAndStatusIgnoreCase(
                                currentUserId,
                                status.trim(),
                                pageable
                        );
            } else if (hasSearch) {
                ticketPage = ticketRepository
                        .findByCreatedByIdAndTitleContainingIgnoreCase(
                                currentUserId,
                                searchText,
                                pageable
                        );
            } else {
                ticketPage = ticketRepository
                        .findByCreatedById(currentUserId, pageable);
            }

        } else if (currentUserRole == Role.AGENT) {

            if (hasStatus && hasSearch) {
                ticketPage = ticketRepository
                        .findByAssignedToIdAndStatusIgnoreCaseAndTitleContainingIgnoreCase(
                                currentUserId,
                                status.trim(),
                                searchText,
                                pageable
                        );
            } else if (hasStatus) {
                ticketPage = ticketRepository
                        .findByAssignedToIdAndStatusIgnoreCase(
                                currentUserId,
                                status.trim(),
                                pageable
                        );
            } else if (hasSearch) {
                ticketPage = ticketRepository
                        .findByAssignedToIdAndTitleContainingIgnoreCase(
                                currentUserId,
                                searchText,
                                pageable
                        );
            } else {
                ticketPage = ticketRepository
                        .findByAssignedToId(currentUserId, pageable);
            }

        } else {
            throw new ForbiddenException(
                    "You are not allowed to access tickets"
            );
        }

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

        // Customers cannot change ticket status.
        if (currentUserRole == Role.CUSTOMER
                && !ticket.getStatus().equals(request.status())) {
            throw new ForbiddenException(
                    "Customers cannot change ticket status"
            );
        }

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());

        // Only agents and admins can change ticket status.
        if (currentUserRole == Role.AGENT
                || currentUserRole == Role.ADMIN) {
            ticket.setStatus(request.status());
        }

        Ticket updatedTicket = ticketRepository.save(ticket);

        return mapToResponse(updatedTicket);
    }

    public TicketResponse assignTicket(
            Long ticketId,
            Long agentId,
            Long currentUserId,
            Role currentUserRole
    ) {
        // Only administrators can assign tickets.
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

        ticket.setAssignedTo(agent);

        Ticket updatedTicket = ticketRepository.save(ticket);

        return mapToResponse(updatedTicket);
    }

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

    private Ticket findTicketById(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Ticket not found with ID: " + ticketId
                        )
                );
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with ID: " + userId
                        )
                );
    }

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
}
