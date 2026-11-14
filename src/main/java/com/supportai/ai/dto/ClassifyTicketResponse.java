package com.supportai.ai.dto;

import com.supportai.ticket.entity.TicketCategory;

public record ClassifyTicketResponse(
        TicketCategory category
) {
}