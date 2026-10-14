//Exposes the HTTP endpoints for adding and reading comments
package com.supportai.ticket.comment.controller;

import com.supportai.exception.ForbiddenException;
import com.supportai.ticket.comment.dto.CommentResponse;
import com.supportai.ticket.comment.dto.CreateCommentRequest;
import com.supportai.ticket.comment.service.TicketCommentService;
import com.supportai.user.entity.Role;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // Retrieve the conversation history for a ticket.
    @GetMapping
    public List<CommentResponse> getComments(
            @PathVariable Long ticketId,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        return commentService.getComments(
                ticketId,
                currentUserId,
                currentUserRole);
    }

    // Extract the application role from Spring Security's authorities.
    private Role getCurrentUserRole(Authentication authentication) {
        String authority = authentication.getAuthorities()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ForbiddenException("User role not found"))
                .getAuthority();

        return Role.valueOf(authority.replace("ROLE_", ""));
    }
}
