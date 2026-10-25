import { apiClient } from './client';
import type { PageResponse } from '@/types/api.types';
import type {
  TicketResponse,
  CreateTicketRequest,
  UpdateTicketRequest,
  TicketQueryParams,
} from '@/types/ticket.types';

/**
 * Fetches paginated, sorted, and filtered tickets.
 * Scoping is applied automatically by the backend based on caller's role.
 * Matches GET /api/tickets
 */
export async function getTicketsApi(
  params: TicketQueryParams = {}
): Promise<PageResponse<TicketResponse>> {
  const cleanParams: Record<string, unknown> = {};
  Object.entries(params).forEach(([key, val]) => {
    if (val !== undefined && val !== null && val !== '') {
      cleanParams[key] = val;
    }
  });

  const response = await apiClient.get<PageResponse<TicketResponse>>('/tickets', {
    params: cleanParams,
  });
  return response.data;
}

/**
 * Fetches a single ticket by ID.
 * Access is role-checked by the backend.
 * Matches GET /api/tickets/{id}
 */
export async function getTicketByIdApi(id: number): Promise<TicketResponse> {
  const response = await apiClient.get<TicketResponse>(`/tickets/${id}`);
  return response.data;
}

/**
 * Creates a new ticket.
 * If category is null or omitted, backend AI classifier will categorize it.
 * Matches POST /api/tickets
 */
export async function createTicketApi(
  data: CreateTicketRequest
): Promise<TicketResponse> {
  const response = await apiClient.post<TicketResponse>('/tickets', data, {
    timeout: 90000,
  });
  return response.data;
}

/**
 * Updates a ticket's fields and transitions status.
 * Customers cannot alter status. Agents/Admins can transition status forward only.
 * Matches PUT /api/tickets/{id}
 */
export async function updateTicketApi(
  id: number,
  data: UpdateTicketRequest
): Promise<TicketResponse> {
  const response = await apiClient.put<TicketResponse>(`/tickets/${id}`, data);
  return response.data;
}

/**
 * Assigns a ticket to a specific agent.
 * Admin role only.
 * Matches PATCH /api/tickets/{id}/assign
 */
export async function assignTicketApi(
  id: number,
  agentId: number
): Promise<TicketResponse> {
  const response = await apiClient.patch<TicketResponse>(`/tickets/${id}/assign`, {
    agentId,
  });
  return response.data;
}

/**
 * Deletes a ticket.
 * Permitted for Admins (any ticket) and Agents (assigned tickets only). Customers are forbidden.
 * Matches DELETE /api/tickets/{id}
 */
export async function deleteTicketApi(id: number): Promise<void> {
  await apiClient.delete(`/tickets/${id}`);
}
