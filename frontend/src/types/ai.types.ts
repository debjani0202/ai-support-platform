import type { TicketCategory } from './ticket.types';

export interface ClassifyTicketRequest {
  title: string;
  description: string;
}

export interface ClassifyTicketResponse {
  category: TicketCategory | null;
}
