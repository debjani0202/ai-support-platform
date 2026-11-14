package com.supportai.ai.dto;

import com.supportai.ticket.entity.TicketPriority;

public record SuggestTicketPriorityResponse(
        TicketPriority priority
) {
}
