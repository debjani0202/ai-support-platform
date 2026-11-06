package com.supportai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectAgentRequestDto(
        @NotBlank(message = "Rejection reason is required")
        @Size(min = 5, max = 1000, message = "Rejection reason must be between 5 and 1000 characters")
        String rejectionReason
) {
}
