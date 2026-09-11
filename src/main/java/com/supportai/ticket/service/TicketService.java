package com.supportai.ticket.service;

import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.dto.CreateTicketRequest;
import com.supportai.ticket.dto.PageResponse;
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

@Service
public class TicketService {

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
            CreateTicketRequest request,
            Long currentUserId
    ) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Ticket ticket = new Ticket();

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());

        // Ticket.status is a String in your entity
        ticket.setStatus("OPEN");

        ticket.setCreatedBy(currentUser);

        Ticket savedTicket = ticketRepository.save(ticket);

        return toResponse(savedTicket);
    }

    public PageResponse<TicketResponse> getTickets(
            int page,
            int size,
            String sortBy,
            String direction,
            String status,
            String search,
            Long currentUserId,
            Role currentUserRole
    ) {
        Sort.Direction sortDirection =
                direction.equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        Page<Ticket> ticketPage = findTicketsByRole(
                currentUserId,
                currentUserRole,
                status,
                search,
                pageable
        );

        return new PageResponse<>(
                ticketPage.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),

                ticketPage.getNumber(),
                ticketPage.getSize(),
                ticketPage.getTotalElements(),
                ticketPage.getTotalPages(),
                ticketPage.isFirst(),
                ticketPage.isLast()
        );
    }

    private Page<Ticket> findTicketsByRole(
            Long currentUserId,
            Role currentUserRole,
            String status,
            String search,
            Pageable pageable
    ) {
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        if (currentUserRole == Role.ADMIN) {

            if (hasStatus && hasSearch) {
                return ticketRepository
                        .findByStatusIgnoreCaseAndTitleContainingIgnoreCase(
                                status,
                                search,
                                pageable
                        );
            }

            if (hasStatus) {
                return ticketRepository.findByStatusIgnoreCase(
                        status,
                        pageable
                );
            }

            if (hasSearch) {
                return ticketRepository
                        .findByTitleContainingIgnoreCase(
                                search,
                                pageable
                        );
            }

            return ticketRepository.findAll(pageable);
        }

        if (currentUserRole == Role.CUSTOMER) {

            if (hasStatus && hasSearch) {
                return ticketRepository
                        .findByCreatedByIdAndStatusIgnoreCaseAndTitleContainingIgnoreCase(
                                currentUserId,
                                status,
                                search,
                                pageable
                        );
            }

            if (hasStatus) {
                return ticketRepository
                        .findByCreatedByIdAndStatusIgnoreCase(
                                currentUserId,
                                status,
                                pageable
                        );
            }

            if (hasSearch) {
                return ticketRepository
                        .findByCreatedByIdAndTitleContainingIgnoreCase(
                                currentUserId,
                                search,
                                pageable
                        );
            }

            return ticketRepository.findByCreatedById(
                    currentUserId,
                    pageable
            );
        }

        if (currentUserRole == Role.AGENT) {

            if (hasStatus && hasSearch) {
                return ticketRepository
                        .findByAssignedToIdAndStatusIgnoreCaseAndTitleContainingIgnoreCase(
                                currentUserId,
                                status,
                                search,
                                pageable
                        );
            }

            if (hasStatus) {
                return ticketRepository
                        .findByAssignedToIdAndStatusIgnoreCase(
                                currentUserId,
                                status,
                                pageable
                        );
            }

            if (hasSearch) {
                return ticketRepository
                        .findByAssignedToIdAndTitleContainingIgnoreCase(
                                currentUserId,
                                search,
                                pageable
                        );
            }

            return ticketRepository.findByAssignedToId(
                    currentUserId,
                    pageable
            );
        }

        throw new ForbiddenException(
                "You are not allowed to view tickets"
        );
    }

    public TicketResponse getTicketById(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Ticket not found")
                );

        validateReadAccess(
                ticket,
                currentUserId,
                currentUserRole
        );

        return toResponse(ticket);
    }

    private void validateReadAccess(
            Ticket ticket,
            Long currentUserId,
            Role currentUserRole
    ) {
        if (currentUserRole == Role.ADMIN) {
            return;
        }

        if (currentUserRole == Role.CUSTOMER) {
            if (ticket.getCreatedBy() == null
                    || !currentUserId.equals(
                            ticket.getCreatedBy().getId()
                    )) {

                throw new ForbiddenException(
                        "You can only view tickets created by you"
                );
            }

            return;
        }

        if (currentUserRole == Role.AGENT) {
            if (ticket.getAssignedTo() == null
                    || !currentUserId.equals(
                            ticket.getAssignedTo().getId()
                    )) {

                throw new ForbiddenException(
                        "You can only view tickets assigned to you"
                );
            }

            return;
        }

        throw new ForbiddenException(
                "You are not allowed to view this ticket"
        );
    }

    public TicketResponse updateTicket(
            Long ticketId,
            UpdateTicketRequest request,
            Long currentUserId,
            Role currentUserRole
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Ticket not found")
                );

        validateReadAccess(
                ticket,
                currentUserId,
                currentUserRole
        );

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(request.status());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());

        Ticket updatedTicket = ticketRepository.save(ticket);

        return toResponse(updatedTicket);
    }

    public void deleteTicket(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Ticket not found")
                );

        validateReadAccess(
                ticket,
                currentUserId,
                currentUserRole
        );

        ticketRepository.delete(ticket);
    }

    private TicketResponse toResponse(Ticket ticket) {

        Long createdById = ticket.getCreatedBy() != null
                ? ticket.getCreatedBy().getId()
                : null;

        Long assignedToId = ticket.getAssignedTo() != null
                ? ticket.getAssignedTo().getId()
                : null;

        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getCategory(),
                ticket.getPriority(),
                createdById,
                assignedToId,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}