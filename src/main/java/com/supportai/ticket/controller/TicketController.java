package com.supportai.ticket.controller;

import com.supportai.ticket.dto.CreateTicketRequest;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.dto.TicketResponse;
import com.supportai.ticket.dto.UpdateTicketRequest;
import com.supportai.user.entity.Role;
import com.supportai.ticket.service.TicketService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        Long currentUserId = Long.valueOf(authentication.getName());

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

            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());

        Role currentUserRole = getCurrentUserRole(authentication);

        return ticketService.getTickets(
                page,
                size,
                sortBy,
                direction,
                status,
                search,
                currentUserId,
                currentUserRole
        );
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());

        Role currentUserRole = getCurrentUserRole(authentication);

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
        Long currentUserId = Long.valueOf(authentication.getName());

        Role currentUserRole = getCurrentUserRole(authentication);

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
        Long currentUserId = Long.valueOf(authentication.getName());

        Role currentUserRole = getCurrentUserRole(authentication);

        ticketService.deleteTicket(
                id,
                currentUserId,
                currentUserRole
        );
    }

    private Role getCurrentUserRole(Authentication authentication) {

        String authority = authentication.getAuthorities()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("User role not found")
                )
                .getAuthority();

        return Role.valueOf(
                authority.replace("ROLE_", "")
        );
    }
}