package com.supportai.ticket.dto;

import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;

import java.time.LocalDateTime;

public record TicketResponse(

        Long id,

        String title,

        String description,

        String status,

        TicketCategory category,

        TicketPriority priority,

        Long createdBy,

        Long assignedTo,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}