
package com.supportai.audit.controller;

import com.supportai.audit.dto.TicketAuditLogResponse;
import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.entity.Role;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the API endpoint for viewing ticket audit history.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/audit-logs")
public class TicketAuditLogController {

    private final TicketAuditLogService auditLogService;

    public TicketAuditLogController(
            TicketAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Returns audit history with pagination for a ticket the authenticated user
     * is authorized to access.
     */
    @GetMapping
    public PageResponse<TicketAuditLogResponse> getHistory(
            @PathVariable Long ticketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // The JWT subject contains the authenticated user's database ID.
        Long currentUserId = Long.valueOf(authentication.getName());

        // Convert ROLE_CUSTOMER into the Role enum value CUSTOMER.
        Role currentUserRole = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .map(Role::valueOf)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user has no role"));

        return auditLogService.getHistory(
                ticketId,
                currentUserId,
                currentUserRole,
                page,
                size
        );
    }
}
