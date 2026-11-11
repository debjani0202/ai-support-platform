import { apiClient } from './client';
import type { PageResponse } from '@/types/api.types';
import type { TicketAuditLogResponse } from '@/types/audit.types';

/**
 * Fetches the audit history of a ticket in chronological order.
 * Requests page 0 with size 50 and unwraps the content array
 * to maintain backward compatibility with existing UI components.
 * Matches GET /api/tickets/{ticketId}/audit-logs
 */
export async function getTicketAuditLogsApi(
  ticketId: number
): Promise<TicketAuditLogResponse[]> {
  const response = await apiClient.get<PageResponse<TicketAuditLogResponse>>(
    `/tickets/${ticketId}/audit-logs`,
    {
      params: {
        page: 0,
        size: 50,
      },
    }
  );
  return response.data.content;
}
