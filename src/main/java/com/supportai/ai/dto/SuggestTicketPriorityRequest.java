package com.supportai.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuggestTicketPriorityRequest(

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
        String description
) {
}
