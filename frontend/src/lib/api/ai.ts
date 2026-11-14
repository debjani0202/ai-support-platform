import { apiClient } from './client';
import type {
  ClassifyTicketRequest,
  ClassifyTicketResponse,
  SuggestPriorityRequest,
  SuggestPriorityResponse,
} from '@/types/ai.types';

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

/**
 * Previews an AI-suggested priority for a ticket draft without creating it.
 * Matches POST /api/ai/suggest-priority
 */
export async function suggestPriorityApi(
  data: SuggestPriorityRequest
): Promise<SuggestPriorityResponse> {
  const response = await apiClient.post<SuggestPriorityResponse>('/ai/suggest-priority', data, {
    timeout: 30000,
  });
  return response.data;
}
