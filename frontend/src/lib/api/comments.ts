import { apiClient } from './client';
import type { PageResponse } from '@/types/api.types';
import type { CommentResponse, CreateCommentRequest } from '@/types/comment.types';

/**
 * Fetches all comments for a ticket in chronological order.
 * Requests page 0 with size 50 and unwraps the content array
 * to maintain backward compatibility with existing UI components.
 * Matches GET /api/tickets/{ticketId}/comments
 */
export async function getTicketCommentsApi(
  ticketId: number
): Promise<CommentResponse[]> {
  const response = await apiClient.get<PageResponse<CommentResponse>>(
    `/tickets/${ticketId}/comments`,
    {
      params: {
        page: 0,
        size: 50,
      },
    }
  );
  return response.data.content;
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
