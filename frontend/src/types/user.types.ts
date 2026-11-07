import type { Role } from './auth.types';

/**
 * Safe projection of support agent user details.
 * Matches com.supportai.user.dto.AgentResponse
 */
export interface AgentResponse {
  id: number;
  name: string;
  email: string;
  role: Role;
}

