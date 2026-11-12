import { apiClient } from './client';
import type {
  AttachmentDownloadResult,
  AttachmentListParams,
  AttachmentPageResponse,
  AttachmentResponse,
  UploadAttachmentRequest,
} from '@/types/attachment.types';

const ATTACHMENT_TRANSFER_TIMEOUT_MS = 60000;
const DEFAULT_FALLBACK_FILENAME = 'attachment';

function isControlOrBidiCodePoint(code: number): boolean {
  return (
    code <= 0x1f ||
    (code >= 0x7f && code <= 0x9f) ||
    code === 0x061c ||
    code === 0x200e ||
    code === 0x200f ||
    (code >= 0x202a && code <= 0x202e) ||
    (code >= 0x2066 && code <= 0x2069)
  );
}

function stripControlAndBidiChars(value: string): string {
  let result = '';
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i);
    if (!isControlOrBidiCodePoint(code)) {
      result += value[i];
    }
  }
  return result;
}

/**
 * Sanitizes a candidate attachment filename by stripping control/bidi characters,
 * path segments, traversal sequences, and unsafe characters.
 * Returns an empty string when no safe filename remains.
 */
function sanitizeCandidateFilename(value: string | undefined | null): string {
  if (!value) {
    return '';
  }

  const stripped = stripControlAndBidiChars(value)
    .replace(/["<>|?*]/g, '')
    .trim();

  if (!stripped) {
    return '';
  }

  const segments = stripped.split(/[/\\]+/).filter(Boolean);
  const terminalSegment = segments.length > 0 ? segments[segments.length - 1] : '';
  if (!terminalSegment) {
    return '';
  }

  const withoutDrive = terminalSegment.replace(/^[a-zA-Z]:/, '');
  const withoutTraversal = withoutDrive.replace(/\.{2,}/g, '.');
  const trimmed = withoutTraversal.replace(/^[.\s]+|[.\s]+$/g, '');

  if (!trimmed || trimmed === '.' || trimmed === '..') {
    return '';
  }

  return trimmed;
}

/**
 * Splits a Content-Disposition header into semicolon-delimited parameters
 * while preserving semicolons inside double-quoted values.
 */
function splitHeaderParameters(headerValue: string): string[] {
  const parts: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < headerValue.length; i++) {
    const ch = headerValue[i];

    // Preserve backslashes in quoted Windows paths while still supporting escaped quotes (\").
    if (ch === '\\' && inQuotes && i + 1 < headerValue.length && headerValue[i + 1] === '"') {
      current += '"';
      i += 1;
      continue;
    }

    if (ch === '"') {
      inQuotes = !inQuotes;
      current += ch;
      continue;
    }

    if (ch === ';' && !inQuotes) {
      if (current.trim()) {
        parts.push(current.trim());
      }
      current = '';
      continue;
    }

    current += ch;
  }

  if (current.trim()) {
    parts.push(current.trim());
  }

  return parts;
}

function unquoteValue(raw: string): string {
  const trimmed = raw.trim();
  if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}

/**
 * Parses and sanitizes the download filename from a Content-Disposition header.
 * Prefers RFC 5987 `filename*=` (UTF-8), falling back to `filename=`, and then to a safe fallback.
 */
export function parseContentDispositionFilename(
  headerValue?: string | null,
  fallbackFileName: string = DEFAULT_FALLBACK_FILENAME
): string {
  const safeFallback = sanitizeCandidateFilename(fallbackFileName) || DEFAULT_FALLBACK_FILENAME;

  if (!headerValue || typeof headerValue !== 'string') {
    return safeFallback;
  }

  const parameters = splitHeaderParameters(headerValue);
  let extendedFilename: string | undefined;
  let standardFilename: string | undefined;

  for (const param of parameters) {
    const equalsIndex = param.indexOf('=');
    if (equalsIndex <= 0) {
      continue;
    }

    const key = param.slice(0, equalsIndex).trim().toLowerCase();
    const rawValue = unquoteValue(param.slice(equalsIndex + 1));

    if (key === 'filename*') {
      // RFC 5987 format: charset'language'value (e.g., UTF-8''encoded%20name.pdf)
      const firstQuote = rawValue.indexOf("'");
      const secondQuote = firstQuote >= 0 ? rawValue.indexOf("'", firstQuote + 1) : -1;
      const encodedValue = secondQuote >= 0 ? rawValue.slice(secondQuote + 1) : rawValue;
      try {
        extendedFilename = decodeURIComponent(encodedValue);
      } catch {
        extendedFilename = undefined;
      }
    } else if (key === 'filename') {
      standardFilename = rawValue;
    }
  }

  const cleanExtended = sanitizeCandidateFilename(extendedFilename);
  if (cleanExtended) {
    return cleanExtended;
  }

  const cleanStandard = sanitizeCandidateFilename(standardFilename);
  if (cleanStandard) {
    return cleanStandard;
  }

  return safeFallback;
}

/**
 * Reads a response header case-insensitively across AxiosHeaders instances and plain objects.
 */
function getHeaderValue(headers: unknown, headerName: string): string | undefined {
  if (!headers || typeof headers !== 'object') {
    return undefined;
  }

  const axiosHeaders = headers as { get?: (name: string) => unknown };
  if (typeof axiosHeaders.get === 'function') {
    const value = axiosHeaders.get(headerName);
    if (typeof value === 'string') {
      return value;
    }
  }

  const targetLower = headerName.toLowerCase();
  for (const [key, value] of Object.entries(headers as Record<string, unknown>)) {
    if (key.toLowerCase() === targetLower && typeof value === 'string') {
      return value;
    }
  }

  return undefined;
}

/**
 * Fetches paginated attachments for a ticket in ascending chronological order.
 * Matches GET /api/tickets/{ticketId}/attachments
 */
export async function getTicketAttachmentsApi(
  ticketId: number,
  params: AttachmentListParams = {}
): Promise<AttachmentPageResponse> {
  const page = params.page ?? 0;
  const size = params.size ?? 20;

  const response = await apiClient.get<AttachmentPageResponse>(
    `/tickets/${ticketId}/attachments`,
    {
      params: { page, size },
    }
  );
  return response.data;
}

/**
 * Uploads a single file attachment to a non-closed ticket using multipart/form-data.
 * Matches POST /api/tickets/{ticketId}/attachments
 */
export async function uploadTicketAttachmentApi(
  request: UploadAttachmentRequest
): Promise<AttachmentResponse> {
  const formData = new FormData();
  formData.append('file', request.file);

  const response = await apiClient.post<AttachmentResponse>(
    `/tickets/${request.ticketId}/attachments`,
    formData,
    {
      timeout: ATTACHMENT_TRANSFER_TIMEOUT_MS,
      signal: request.signal,
      onUploadProgress: request.onUploadProgress
        ? (progressEvent) => {
            const total = progressEvent.total ?? request.file.size;
            if (total > 0) {
              const percent = Math.min(
                100,
                Math.max(0, Math.round((progressEvent.loaded * 100) / total))
              );
              request.onUploadProgress?.(percent);
            }
          }
        : undefined,
    }
  );
  return response.data;
}

/**
 * Downloads an attachment binary as a Blob alongside its sanitized filename and content type.
 * Matches GET /api/tickets/{ticketId}/attachments/{attachmentId}/download
 */
export async function downloadTicketAttachmentApi(
  ticketId: number,
  attachmentId: number,
  fallbackFileName?: string,
  signal?: AbortSignal
): Promise<AttachmentDownloadResult> {
  const response = await apiClient.get<Blob>(
    `/tickets/${ticketId}/attachments/${attachmentId}/download`,
    {
      responseType: 'blob',
      timeout: ATTACHMENT_TRANSFER_TIMEOUT_MS,
      signal,
    }
  );

  const contentDisposition = getHeaderValue(response.headers, 'content-disposition');
  const headerContentType = getHeaderValue(response.headers, 'content-type');
  const fileName = parseContentDispositionFilename(contentDisposition, fallbackFileName);
  const contentType =
    headerContentType ||
    (response.data instanceof Blob && response.data.type ? response.data.type : '') ||
    'application/octet-stream';

  return {
    blob: response.data,
    fileName,
    contentType,
  };
}

/**
 * Deletes an attachment from an active non-closed ticket.
 * Matches DELETE /api/tickets/{ticketId}/attachments/{attachmentId}
 */
export async function deleteTicketAttachmentApi(
  ticketId: number,
  attachmentId: number
): Promise<void> {
  await apiClient.delete(`/tickets/${ticketId}/attachments/${attachmentId}`);
}
