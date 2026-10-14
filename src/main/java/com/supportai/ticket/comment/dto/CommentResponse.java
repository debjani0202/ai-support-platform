//Validate incoming comment content and define the response returned by the API.
package com.supportai.ticket.comment.dto;

import java.time.LocalDateTime;

// Defines the safe data returned to the client after reading a comment.
//
public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorName,
        String content,
        LocalDateTime createdAt
) {
}
