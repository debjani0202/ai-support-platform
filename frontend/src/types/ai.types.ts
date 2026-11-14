import type { TicketCategory, TicketPriority } from './ticket.types';

export interface ClassifyTicketRequest {
  title: string;
  description: string;
}

export interface ClassifyTicketResponse {
  category: TicketCategory | null;
}

export interface SuggestPriorityRequest {
  title: string;
  description: string;
}

export interface SuggestPriorityResponse {
  priority: TicketPriority | null;
}
