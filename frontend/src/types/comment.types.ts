/**
 * Matches com.supportai.ticket.comment.dto.CommentResponse
 */
export interface CommentResponse {
  id: number;
  ticketId: number;
  authorId: number;
  authorName: string;
  content: string;
  createdAt: string; // ISO-8601 string from backend LocalDateTime
}

/**
 * Matches com.supportai.ticket.comment.dto.CreateCommentRequest
 */
export interface CreateCommentRequest {
  content: string;
}
