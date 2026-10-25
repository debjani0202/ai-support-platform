import { apiClient } from './client';
import type { HealthResponse } from '@/types/api.types';

/**
 * Calls the backend health check endpoint GET /api/health.
 * Matches com.supportai.health.HealthController
 */
export async function fetchHealth(): Promise<HealthResponse> {
  const response = await apiClient.get<HealthResponse>('/health');
  return response.data;
}
