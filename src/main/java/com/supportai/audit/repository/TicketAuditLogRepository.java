
package com.supportai.audit.repository;

import com.supportai.audit.entity.TicketAuditLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Provides database operations for ticket audit records.
 * Spring Data JPA implements this interface automatically.
 */
public interface TicketAuditLogRepository
        extends JpaRepository<TicketAuditLog, Long> {

    /**
     * Retrieves audit records for a specific ticket.
     * The caller supplies the sort order.
     */
    List<TicketAuditLog> findByTicketId(Long ticketId, Sort sort);
}
