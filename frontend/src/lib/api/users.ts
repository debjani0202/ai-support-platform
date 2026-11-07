import { apiClient } from './client';
import type { AgentResponse } from '@/types/user.types';

/**
 * Retrieves all users with the AGENT role.
 * Requires ADMIN or AGENT role.
 * Matches GET /api/users/agents
 */
export async function getAgentsApi(): Promise<AgentResponse[]> {
  const response = await apiClient.get<AgentResponse[]>('/users/agents');
  return response.data;
}

