import { apiClient } from './client';
import type { CommentResponse, CreateCommentRequest } from '@/types/comment.types';

/**
 * Fetches all comments for a ticket in chronological order.
 * Matches GET /api/tickets/{ticketId}/comments
 */
export async function getTicketCommentsApi(
  ticketId: number
): Promise<CommentResponse[]> {
  const response = await apiClient.get<CommentResponse[]>(
    `/tickets/${ticketId}/comments`
  );
  return response.data;
}

/**
 * Adds a new comment to a ticket.
 * Matches POST /api/tickets/{ticketId}/comments
 */
export async function createTicketCommentApi(
  ticketId: number,
  data: CreateCommentRequest
): Promise<CommentResponse> {
  const response = await apiClient.post<CommentResponse>(
    `/tickets/${ticketId}/comments`,
    data
  );
  return response.data;
}
