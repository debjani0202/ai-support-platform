package com.supportai.ticket.dto;

import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(

        @NotBlank(message = "Title is required")
        @Size(
                max = 255,
                message = "Title must not exceed 255 characters"
        )
        String title,

        @NotBlank(message = "Description is required")
        @Size(
                max = 10000,
                message = "Description must not exceed 10000 characters"
        )
        String description,

        @NotBlank(message = "Status is required")
        @Pattern(
                regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED",
                message = "Status must be OPEN, IN_PROGRESS, RESOLVED or CLOSED"
        )
        String status,

        TicketCategory category,

        @NotNull(message = "Priority is required")
        TicketPriority priority
) {
}