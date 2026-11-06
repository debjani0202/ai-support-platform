export type AgentRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface AgentRequestResponse {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  status: AgentRequestStatus;
  reason: string | null;
  adminNotes: string | null;
  rejectionReason: string | null;
  reviewedBy: number | null;
  reviewerName: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAgentRequestDto {
  reason?: string;
}

export interface ApproveAgentRequestDto {
  adminNotes?: string;
}

export interface RejectAgentRequestDto {
  rejectionReason: string;
}

export interface AgentRequestFilterParams {
  page?: number;
  size?: number;
  status?: AgentRequestStatus;
}
