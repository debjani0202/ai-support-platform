package com.supportai.ticket.repository;

import com.supportai.ticket.dto.TicketStatsResponse;
import com.supportai.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketRepository
        extends JpaRepository<Ticket, Long>,
                JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT new com.supportai.ticket.dto.TicketStatsResponse(
                COUNT(t.id),
                COUNT(CASE WHEN t.status = 'OPEN' THEN 1 END),
                COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END),
                COUNT(CASE WHEN t.status = 'RESOLVED' THEN 1 END)
            )
            FROM Ticket t
            WHERE t.deletedAt IS NULL
            """)
    TicketStatsResponse getTicketStatsForAdmin();

    @Query("""
            SELECT new com.supportai.ticket.dto.TicketStatsResponse(
                COUNT(t.id),
                COUNT(CASE WHEN t.status = 'OPEN' THEN 1 END),
                COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END),
                COUNT(CASE WHEN t.status = 'RESOLVED' THEN 1 END)
            )
            FROM Ticket t
            WHERE t.deletedAt IS NULL
              AND t.createdBy.id = :customerId
            """)
    TicketStatsResponse getTicketStatsForCustomer(@Param("customerId") Long customerId);

    @Query("""
            SELECT new com.supportai.ticket.dto.TicketStatsResponse(
                COUNT(t.id),
                COUNT(CASE WHEN t.status = 'OPEN' THEN 1 END),
                COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END),
                COUNT(CASE WHEN t.status = 'RESOLVED' THEN 1 END)
            )
            FROM Ticket t
            WHERE t.deletedAt IS NULL
              AND t.assignedTo.id = :agentId
            """)
    TicketStatsResponse getTicketStatsForAgent(@Param("agentId") Long agentId);
}
