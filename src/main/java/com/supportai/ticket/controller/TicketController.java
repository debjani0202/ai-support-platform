package com.supportai.ticket.controller;

import com.supportai.exception.ForbiddenException;
import com.supportai.ticket.dto.AssignTicketRequest;
import com.supportai.ticket.dto.CreateTicketRequest;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.dto.UpdateTicketRequest;
import com.supportai.ticket.service.TicketService;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.user.entity.Role;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(
                authentication.getName()
        );

        return ticketService.createTicket(
                request,
                currentUserId
        );
    }

    @GetMapping
    public PageResponse<TicketResponse> getTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) Long assignedAgentId,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(
                authentication.getName()
        );

        Role currentUserRole = getCurrentUserRole(
                authentication
        );

        return ticketService.getTickets(
                page,
                size,
                sortBy,
                direction,
                status,
                search,
                priority,
                category,
                assignedAgentId,
                currentUserId,
                currentUserRole
        );
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(
                authentication.getName()
        );

        Role currentUserRole = getCurrentUserRole(
                authentication
        );

        return ticketService.getTicketById(
                id,
                currentUserId,
                currentUserRole
        );
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(
                authentication.getName()
        );

        Role currentUserRole = getCurrentUserRole(
                authentication
        );

        return ticketService.updateTicket(
                id,
                request,
                currentUserId,
                currentUserRole
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicket(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(
                authentication.getName()
        );

        Role currentUserRole = getCurrentUserRole(
                authentication
        );

        ticketService.deleteTicket(
                id,
                currentUserId,
                currentUserRole
        );
    }

    @PatchMapping("/{id}/assign")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(
                authentication.getName()
        );

        Role currentUserRole = getCurrentUserRole(
                authentication
        );

        return ticketService.assignTicket(
                id,
                request.agentId(),
                currentUserId,
                currentUserRole
        );
    }

    private Role getCurrentUserRole(
            Authentication authentication
    ) {
        String authority = authentication.getAuthorities()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ForbiddenException(
                                "User role not found"
                        )
                )
                .getAuthority();

        return Role.valueOf(
                authority.replace("ROLE_", "")
        );
    }
}
