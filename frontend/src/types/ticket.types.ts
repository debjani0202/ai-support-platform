export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export type TicketCategory =
  | 'ACCOUNT_ACCESS'
  | 'PAYMENT'
  | 'TECHNICAL_ISSUE'
  | 'BUG_REPORT'
  | 'FEATURE_REQUEST'
  | 'GENERAL_QUERY';

/**
 * Matches com.supportai.ticket.dto.TicketResponse
 */
export interface TicketResponse {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  category: TicketCategory | null;
  priority: TicketPriority;
  createdBy: number;
  assignedTo: number | null;
  createdAt: string; // ISO-8601 string from backend LocalDateTime
  updatedAt: string; // ISO-8601 string from backend LocalDateTime
}

/**
 * Matches com.supportai.ticket.dto.CreateTicketRequest
 */
export interface CreateTicketRequest {
  title: string;
  description: string;
  priority: TicketPriority;
  category?: TicketCategory | null;
}

/**
 * Matches com.supportai.ticket.dto.UpdateTicketRequest
 */
export interface UpdateTicketRequest {
  title: string;
  description: string;
  status: TicketStatus;
  category?: TicketCategory | null;
  priority: TicketPriority;
}

/**
 * Supported query parameters for GET /api/tickets
 * Matches com.supportai.ticket.controller.TicketController#getTickets
 */
export interface TicketQueryParams {
  page?: number;
  size?: number;
  sortBy?: 'id' | 'title' | 'status' | 'priority' | 'category' | 'createdAt' | 'updatedAt';
  direction?: 'asc' | 'desc';
  status?: string;
  priority?: TicketPriority;
  category?: TicketCategory;
  search?: string;
  assignedAgentId?: number; // ADMIN role only
}
