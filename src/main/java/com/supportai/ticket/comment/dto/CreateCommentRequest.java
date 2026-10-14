
package com.supportai.ticket.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Defines the data a user must provide when adding a comment.
public record CreateCommentRequest(

        @NotBlank(message = "Comment cannot be empty")
        @Size(max = 5000, message = "Comment cannot exceed 5000 characters")
        String content

) {
}
