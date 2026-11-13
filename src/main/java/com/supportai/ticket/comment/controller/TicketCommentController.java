
// Exposes the HTTP endpoints for adding and reading comments
package com.supportai.ticket.comment.controller;

import com.supportai.exception.ForbiddenException;
import com.supportai.ticket.comment.dto.CommentResponse;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.comment.dto.CreateCommentRequest;
import com.supportai.ticket.comment.service.TicketCommentService;
import com.supportai.user.entity.Role;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class TicketCommentController {

    private final TicketCommentService commentService;

    public TicketCommentController(TicketCommentService commentService) {
        this.commentService = commentService;
    }

    // Create a comment on a ticket.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        return commentService.addComment(
                ticketId,
                request,
                currentUserId,
                currentUserRole);
    }

    // Retrieve the conversation history for a ticket with pagination.
    @GetMapping
    public PageResponse<CommentResponse> getComments(
            @PathVariable Long ticketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        return commentService.getComments(
                ticketId,
                currentUserId,
                currentUserRole,
                page,
                size);
    }

    // Extract the application role from Spring Security's authorities.
    private Role getCurrentUserRole(Authentication authentication) {

        return authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority != null
                        && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(roleName -> {
                    try {
                        return Role.valueOf(roleName);
                    } catch (IllegalArgumentException ex) {
                        throw new ForbiddenException("Invalid user role");
                    }
                })
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("User role not found"));
    }
}
