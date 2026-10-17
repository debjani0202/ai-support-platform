package com.supportai.notification.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long ticketId,
        String type,
        String message,
        boolean read,
        Instant createdAt
) {
}
