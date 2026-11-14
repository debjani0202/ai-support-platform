import { apiClient } from './client';
import type { ClassifyTicketRequest, ClassifyTicketResponse } from '@/types/ai.types';

/**
 * Previews an AI-suggested category for a ticket draft without creating it.
 * Matches POST /api/ai/classify
 */
export async function classifyTicketApi(
  data: ClassifyTicketRequest
): Promise<ClassifyTicketResponse> {
  const response = await apiClient.post<ClassifyTicketResponse>('/ai/classify', data, {
    timeout: 30000,
  });
  return response.data;
}
