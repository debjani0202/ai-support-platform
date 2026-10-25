import { apiClient } from './client';
import type { TicketAuditLogResponse } from '@/types/audit.types';

/**
 * Fetches the audit history of a ticket in chronological order.
 * Matches GET /api/tickets/{ticketId}/audit-logs
 */
export async function getTicketAuditLogsApi(
  ticketId: number
): Promise<TicketAuditLogResponse[]> {
  const response = await apiClient.get<TicketAuditLogResponse[]>(
    `/tickets/${ticketId}/audit-logs`
  );
  return response.data;
}
