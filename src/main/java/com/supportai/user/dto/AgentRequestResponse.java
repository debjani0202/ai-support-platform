package com.supportai.user.dto;

import com.supportai.user.entity.AgentRequestStatus;

import java.time.LocalDateTime;

public record AgentRequestResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        AgentRequestStatus status,
        String reason,
        String adminNotes,
        String rejectionReason,
        Long reviewedById,
        String reviewedByName,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}
