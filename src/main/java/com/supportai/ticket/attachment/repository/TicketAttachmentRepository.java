package com.supportai.ticket.attachment.repository;

import com.supportai.ticket.attachment.entity.TicketAttachment;
import com.supportai.ticket.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    /**
     * Retrieves paginated attachment metadata belonging to the given ticket,
     * eagerly fetching uploader metadata to avoid N+1 user queries during response mapping.
     */
    @EntityGraph(attributePaths = {"uploadedBy"})
    Page<TicketAttachment> findByTicketId(Long ticketId, Pageable pageable);

    /**
     * Counts current attachments for a ticket.
     * Note: Alone, this does not protect against concurrent quota races under READ COMMITTED;
     * callers enforcing quotas must invoke {@link #findTicketByIdForUpdate(Long)} first in the same transaction.
     */
    long countByTicketId(Long ticketId);

    /**
     * Finds an attachment scoped strictly to the specified ticket ID.
     */
    Optional<TicketAttachment> findByIdAndTicketId(Long id, Long ticketId);

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the parent ticket row
     * so concurrent uploads for the same ticket serialize before counting and inserting attachments.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :ticketId")
    Optional<Ticket> findTicketByIdForUpdate(@Param("ticketId") Long ticketId);
}

