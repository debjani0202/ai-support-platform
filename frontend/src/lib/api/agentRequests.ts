import { apiClient } from './client';
import type { PageResponse } from '@/types/api.types';
import type {
  AgentRequestResponse,
  CreateAgentRequestDto,
  ApproveAgentRequestDto,
  RejectAgentRequestDto,
  AgentRequestFilterParams,
} from '@/types/agentRequest.types';

/**
 * Submits a new agent access request for the authenticated user.
 * POST /api/agent-requests
 */
export async function submitAgentRequestApi(
  dto?: CreateAgentRequestDto
): Promise<AgentRequestResponse> {
  const response = await apiClient.post<AgentRequestResponse>(
    '/agent-requests',
    dto ?? {}
  );
  return response.data;
}

/**
 * Retrieves the latest agent access request for the authenticated user.
 * GET /api/agent-requests/me
 */
export async function getMyAgentRequestApi(): Promise<AgentRequestResponse | null> {
  const response = await apiClient.get<AgentRequestResponse | null>(
    '/agent-requests/me'
  );
  return response.data || null;
}

/**
 * Retrieves paginated agent access requests (ADMIN only).
 * GET /api/agent-requests
 */
export async function getAdminAgentRequestsApi(
  params?: AgentRequestFilterParams
): Promise<PageResponse<AgentRequestResponse>> {
  const response = await apiClient.get<PageResponse<AgentRequestResponse>>(
    '/agent-requests',
    { params }
  );
  return response.data;
}

/**
 * Approves an agent access request and promotes applicant to AGENT (ADMIN only).
 * PATCH /api/agent-requests/:id/approve
 */
export async function approveAgentRequestApi(
  id: number,
  dto?: ApproveAgentRequestDto
): Promise<AgentRequestResponse> {
  const response = await apiClient.patch<AgentRequestResponse>(
    `/agent-requests/${id}/approve`,
    dto ?? {}
  );
  return response.data;
}

/**
 * Rejects an agent access request with mandatory reason (ADMIN only).
 * PATCH /api/agent-requests/:id/reject
 */
export async function rejectAgentRequestApi(
  id: number,
  dto: RejectAgentRequestDto
): Promise<AgentRequestResponse> {
  const response = await apiClient.patch<AgentRequestResponse>(
    `/agent-requests/${id}/reject`,
    dto
  );
  return response.data;
}
