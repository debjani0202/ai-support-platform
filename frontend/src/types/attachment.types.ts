import type { PageResponse } from '@/types/api.types';

/**
 * Ticket attachment metadata returned by the backend attachment API.
 * Matches com.supportai.ticket.attachment.dto.AttachmentResponse
 */
export interface AttachmentResponse {
  id: number;
  ticketId: number;
  uploadedById: number;
  uploadedByName: string;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string; // ISO-8601 string from backend LocalDateTime
}

/**
 * Paginated response type for ticket attachments.
 * Matches PageResponse<AttachmentResponse>
 */
export type AttachmentPageResponse = PageResponse<AttachmentResponse>;

/**
 * Supported query parameters for listing ticket attachments.
 */
export interface AttachmentListParams {
  page?: number;
  size?: number;
}

/**
 * Request payload for uploading an attachment to an existing ticket.
 */
export interface UploadAttachmentRequest {
  ticketId: number;
  file: File;
  onUploadProgress?: (progressPercent: number) => void;
  signal?: AbortSignal;
}

/**
 * Binary download result containing Blob payload and sanitized filename metadata.
 */
export interface AttachmentDownloadResult {
  blob: Blob;
  fileName: string;
  contentType: string;
}

