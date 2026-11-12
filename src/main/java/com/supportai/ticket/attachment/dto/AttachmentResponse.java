package com.supportai.ticket.attachment.dto;

import com.supportai.ticket.attachment.entity.TicketAttachment;

import java.time.LocalDateTime;

/**
 * Safe API representation of ticket attachment metadata.
 * Deliberately excludes internal stored filenames and filesystem paths.
 */
public record AttachmentResponse(
        Long id,
        Long ticketId,
        Long uploadedById,
        String uploadedByName,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {

    /**
     * Converts a TicketAttachment entity into a client-safe AttachmentResponse DTO.
     */
    public static AttachmentResponse from(TicketAttachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getUploadedBy().getId(),
                attachment.getUploadedBy().getName(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getCreatedAt()
        );
    }
}

