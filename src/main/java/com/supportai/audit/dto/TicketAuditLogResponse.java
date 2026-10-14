
package com.supportai.audit.dto;

import com.supportai.audit.entity.TicketAuditLog;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) for returning ticket audit history.
 *
 * We return only the information needed by the API client,
 * rather than exposing the complete JPA entity.
 */
public record TicketAuditLogResponse(
        Long id,
        Long ticketId,
        Long actorId,
        String actorName,
        String action,
        String oldValue,
        String newValue,
        LocalDateTime createdAt
) {

    /**
     * Converts a TicketAuditLog entity into a response DTO.
     *
     * @param log the audit record retrieved from the database
     * @return a response containing the audit record's details
     */
    public static TicketAuditLogResponse from(TicketAuditLog log) {

        return new TicketAuditLogResponse(
                log.getId(),
                log.getTicket().getId(),
                log.getActor().getId(),
                log.getActor().getName(),
                log.getAction(),
                log.getOldValue(),
                log.getNewValue(),
                log.getCreatedAt()
        );
    }
}
