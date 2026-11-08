package com.supportai.ticket.dto;

public record TicketStatsResponse(
        long total,
        long open,
        long inProgress,
        long resolved
) {
}

