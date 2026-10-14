//Reads and saves comments using Spring Data JPA.
package com.supportai.ticket.comment.repository;

import com.supportai.ticket.comment.entity.TicketComment;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface TicketCommentRepository
        extends JpaRepository<TicketComment, Long> {

    // Retrieve a ticket's comments in chronological order.
    List<TicketComment> findByTicketId(
            Long ticketId,
            Sort sort
    );
}
