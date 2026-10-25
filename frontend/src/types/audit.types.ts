/**
 * Matches com.supportai.audit.dto.TicketAuditLogResponse
 */
export interface TicketAuditLogResponse {
  id: number;
  ticketId: number;
  actorId: number;
  actorName: string;
  action: string;
  oldValue: string | null;
  newValue: string | null;
  createdAt: string; // ISO-8601 string from backend LocalDateTime
}
