//Contains business logic, ticket-access authorization, comment creation, and retrieval
package com.supportai.ticket.comment.service;

import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.notification.service.NotificationService;
import com.supportai.ticket.comment.dto.CommentResponse;
import com.supportai.ticket.comment.dto.CreateCommentRequest;
import com.supportai.ticket.comment.entity.TicketComment;
import com.supportai.ticket.comment.repository.TicketCommentRepository;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TicketCommentService {

    private final TicketCommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
public TicketCommentService(
            TicketCommentRepository commentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // Add a comment after verifying the user's access to the ticket.
    //loads the ticket, checks permissions, identifies the authenticated author, and saves the comment.
    public CommentResponse addComment(
            Long ticketId,
            CreateCommentRequest request,
            Long currentUserId,
            Role currentUserRole) {

        Ticket ticket = findTicketById(ticketId);

        // Reuse the existing ticket ownership and assignment rules.
        validateTicketAccess(ticket, currentUserId, currentUserRole);

        User author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + currentUserId));

        TicketComment comment = new TicketComment(
                ticket,
                author,
                request.content().trim());

        TicketComment savedComment = commentRepository.save(comment);

        // Notify the ticket creator unless they wrote the comment.
        User creator = ticket.getCreatedBy();
        if (creator != null && !creator.getId().equals(currentUserId)) {
            notificationService.createNotification(
                    creator,
                    ticket,
                    "NEW_COMMENT",
                    "New comment on ticket #" + ticket.getId()
                            + ": " + ticket.getTitle()
            );
        }

        // Notify the assigned agent, avoiding duplicate recipients.
        User assignedAgent = ticket.getAssignedTo();
        if (assignedAgent != null
                && !assignedAgent.getId().equals(currentUserId)
                && (creator == null
                    || !creator.getId().equals(assignedAgent.getId()))) {
            notificationService.createNotification(
                    assignedAgent,
                    ticket,
                    "NEW_COMMENT",
                    "New comment on ticket #" + ticket.getId()
                            + ": " + ticket.getTitle()
            );
        }

        return mapToResponse(savedComment);
    }

    // Return a ticket's comments from oldest to newest.
    @Transactional(readOnly = true)  //@Transactional annotation keeps database operations within a transaction
    //checks the same permissions and returns comments chronologically.
    public List<CommentResponse> getComments(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole) {

        Ticket ticket = findTicketById(ticketId);

        validateTicketAccess(ticket, currentUserId, currentUserRole);

        Sort chronologicalOrder = Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"));

        return commentRepository.findByTicketId(ticketId, chronologicalOrder)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private Ticket findTicketById(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ticket not found with ID: " + ticketId));

        if (ticket.getDeletedAt() != null) {
            throw new ResourceNotFoundException(
                    "Ticket not found with ID: " + ticketId);
        }

        return ticket;
    }

    private void validateTicketAccess(
            Ticket ticket,
            Long currentUserId,
            Role currentUserRole) {

        // Administrators can access any ticket.
        if (currentUserRole == Role.ADMIN) {
            return;
        }

        // Customers can access only tickets they created.
        if (currentUserRole == Role.CUSTOMER) {
            if (ticket.getCreatedBy() == null
                    || !ticket.getCreatedBy().getId().equals(currentUserId)) {
                throw new ForbiddenException(
                        "You are not allowed to access this ticket");
            }
            return;
        }

        // Agents can access only tickets assigned to them.
        if (currentUserRole == Role.AGENT) {
            if (ticket.getAssignedTo() == null
                    || !ticket.getAssignedTo().getId().equals(currentUserId)) {
                throw new ForbiddenException(
                        "You are not allowed to access this ticket");
            }
            return;
        }

        throw new ForbiddenException(
                "You are not allowed to access this ticket");
    }

    private CommentResponse mapToResponse(TicketComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}
