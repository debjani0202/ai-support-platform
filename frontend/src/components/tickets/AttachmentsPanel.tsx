import React, { useState, useEffect, useRef } from 'react';
import { format, parseISO } from 'date-fns';
import {
  Paperclip,
  Upload,
  Download,
  Trash2,
  FileText,
  Image as ImageIcon,
  AlertCircle,
  Lock,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  X,
  Info,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/hooks/useAuth';
import { uploadTicketAttachmentApi, downloadTicketAttachmentApi } from '@/lib/api/attachments';
import type { AttachmentPageResponse, AttachmentResponse } from '@/types/attachment.types';
import type { TicketStatus } from '@/types/ticket.types';
import { Button } from '@/components/ui/Button';
import { DeleteAttachmentModal } from '@/components/tickets/DeleteAttachmentModal';

export const MAX_ATTACHMENT_SIZE_BYTES = 10_485_760; // 10 MiB
export const MAX_ATTACHMENTS_PER_TICKET = 20;
const ALLOWED_EXTENSIONS = new Set(['pdf', 'png', 'jpg', 'jpeg', 'txt']);
const ACCEPT_ATTRIBUTE = '.pdf,.png,.jpg,.jpeg,.txt';
const PAGE_SIZE_OPTIONS = [5, 10, 20] as const;
const DOWNLOAD_REVOKE_DELAY_MS = 1000;

export interface AttachmentsPanelProps {
  ticketId: number;
  ticketStatus: TicketStatus;
  attachmentsPage: AttachmentPageResponse | null;
  page: number;
  size: number;
  isLoading: boolean;
  error: string | null;
  onPageChange: (nextPage: number) => void;
  onSizeChange: (nextSize: number) => void;
  onRetry: () => void;
  onUploadSuccess: () => Promise<void> | void;
  onDeleteSuccess: () => Promise<void> | void;
  onConflict: () => Promise<void> | void;
}

interface SafeApiError {
  status?: number;
  message?: string;
  code?: string;
  name?: string;
}

function extractSafeApiError(err: unknown): SafeApiError {
  if (!err || typeof err !== 'object') {
    return {};
  }
  const record = err as Record<string, unknown>;
  const rawMessage = typeof record.message === 'string' ? record.message.trim() : undefined;
  return {
    status: typeof record.status === 'number' ? record.status : undefined,
    message: rawMessage && !rawMessage.startsWith('[object ') ? rawMessage : undefined,
    code: typeof record.code === 'string' ? record.code : undefined,
    name: typeof record.name === 'string' ? record.name : undefined,
  };
}

function isCanceledRequest(err: unknown, controller: AbortController | null): boolean {
  if (controller?.signal.aborted) {
    return true;
  }
  const details = extractSafeApiError(err);
  return (
    details.code === 'ERR_CANCELED' ||
    details.name === 'CanceledError' ||
    details.name === 'AbortError'
  );
}

function validateAttachmentFile(file: File): string | null {
  const dotIndex = file.name.lastIndexOf('.');
  const ext =
    dotIndex > 0 && dotIndex < file.name.length - 1
      ? file.name.slice(dotIndex + 1).toLowerCase()
      : '';

  if (!ALLOWED_EXTENSIONS.has(ext)) {
    return 'Unsupported file type. Allowed formats: PDF, PNG, JPG, JPEG, TXT.';
  }
  if (file.size <= 0) {
    return 'File is empty. Please select a non-empty file.';
  }
  if (file.size > MAX_ATTACHMENT_SIZE_BYTES) {
    return 'File exceeds the 10 MiB (10 MB) maximum size limit.';
  }
  return null;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatUploadDate(isoDate: string): string {
  try {
    return format(parseISO(isoDate), 'PPp');
  } catch {
    return isoDate;
  }
}

function renderFileIcon(contentType: string, fileName: string) {
  const lowerType = contentType.toLowerCase();
  const lowerName = fileName.toLowerCase();
  const isImage =
    lowerType.startsWith('image/') ||
    lowerName.endsWith('.png') ||
    lowerName.endsWith('.jpg') ||
    lowerName.endsWith('.jpeg');

  if (isImage) {
    return <ImageIcon className="w-4 h-4 text-indigo-600 shrink-0" />;
  }
  return <FileText className="w-4 h-4 text-indigo-600 shrink-0" />;
}

export function AttachmentsPanel({
  ticketId,
  ticketStatus,
  attachmentsPage,
  page,
  size,
  isLoading,
  error,
  onPageChange,
  onSizeChange,
  onRetry,
  onUploadSuccess,
  onDeleteSuccess,
  onConflict,
}: AttachmentsPanelProps) {
  const { user } = useAuth();

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadNotice, setUploadNotice] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloadingIds, setDownloadingIds] = useState<Set<number>>(() => new Set());
  const [attachmentToDelete, setAttachmentToDelete] = useState<AttachmentResponse | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const uploadAbortRef = useRef<AbortController | null>(null);
  const downloadAbortsRef = useRef<Set<AbortController>>(new Set());
  const pendingRevokesRef = useRef<Map<number, string>>(new Map());
  const isMountedRef = useRef(true);
  const currentTicketIdRef = useRef(ticketId);
  currentTicketIdRef.current = ticketId;

  const isClosed = ticketStatus === 'CLOSED';
  const totalElements = attachmentsPage?.totalElements ?? 0;
  const isQuotaReached = totalElements >= MAX_ATTACHMENTS_PER_TICKET;
  const attachments = attachmentsPage?.content ?? [];

  // Cleanup active requests and deferred Blob object URLs on unmount
  useEffect(() => {
    isMountedRef.current = true;
    const downloadAborts = downloadAbortsRef.current;
    const pendingRevokes = pendingRevokesRef.current;

    return () => {
      isMountedRef.current = false;
      uploadAbortRef.current?.abort();
      uploadAbortRef.current = null;

      downloadAborts.forEach((controller) => controller.abort());
      downloadAborts.clear();

      pendingRevokes.forEach((objectUrl, timerId) => {
        window.clearTimeout(timerId);
        URL.revokeObjectURL(objectUrl);
      });
      pendingRevokes.clear();
    };
  }, []);

  // Reset transient file selection/notices and abort in-flight requests when ticket changes
  useEffect(() => {
    currentTicketIdRef.current = ticketId;
    uploadAbortRef.current?.abort();
    uploadAbortRef.current = null;

    downloadAbortsRef.current.forEach((controller) => controller.abort());
    downloadAbortsRef.current.clear();

    setSelectedFile(null);
    setUploadError(null);
    setUploadNotice(null);
    setDownloadError(null);
    setDownloadingIds(new Set());
    setUploadProgress(0);
    setIsUploading(false);
    setAttachmentToDelete(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, [ticketId]);

  // Empty-last-page safeguard if parent passes an out-of-range page response
  useEffect(() => {
    if (
      !isLoading &&
      !error &&
      attachmentsPage &&
      page > 0 &&
      attachmentsPage.content.length === 0 &&
      attachmentsPage.totalPages > 0
    ) {
      onPageChange(Math.max(0, attachmentsPage.totalPages - 1));
    }
  }, [isLoading, error, attachmentsPage, page, onPageChange]);

  const clearSelectedFile = () => {
    setSelectedFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleFileSelection = (file: File | undefined | null) => {
    setUploadError(null);
    setUploadNotice(null);

    if (!file) {
      return;
    }

    const validationError = validateAttachmentFile(file);
    if (validationError) {
      setUploadError(validationError);
      clearSelectedFile();
      return;
    }

    setSelectedFile(file);
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    handleFileSelection(file);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (isClosed || isQuotaReached || isUploading) {
      return;
    }
    const file = e.dataTransfer.files?.[0];
    handleFileSelection(file);
  };

  const handleUpload = async () => {
    if (!selectedFile || isUploading || isClosed || isQuotaReached) {
      return;
    }

    const requestTicketId = ticketId;
    const controller = new AbortController();
    uploadAbortRef.current = controller;
    setIsUploading(true);
    setUploadProgress(0);
    setUploadError(null);
    setUploadNotice(null);

    try {
      const uploaded = await uploadTicketAttachmentApi({
        ticketId: requestTicketId,
        file: selectedFile,
        signal: controller.signal,
        onUploadProgress: (percent) => {
          if (isMountedRef.current && uploadAbortRef.current === controller) {
            setUploadProgress(percent);
          }
        },
      });

      if (!isMountedRef.current || controller.signal.aborted) {
        if (
          isMountedRef.current &&
          controller.signal.aborted &&
          currentTicketIdRef.current === requestTicketId
        ) {
          setUploadProgress(0);
          setUploadNotice(
            'Upload request was canceled in your browser. Please refresh if the transfer had already completed on the server.'
          );
        }
        return;
      }

      if (currentTicketIdRef.current !== requestTicketId) {
        return;
      }

      clearSelectedFile();
      setUploadProgress(0);
      toast.success(`Uploaded "${uploaded.originalFileName}".`);
      await onUploadSuccess();
    } catch (err: unknown) {
      if (!isMountedRef.current || currentTicketIdRef.current !== requestTicketId) {
        return;
      }

      if (isCanceledRequest(err, controller)) {
        setUploadProgress(0);
        setUploadNotice(
          'Upload request was canceled in your browser. Please refresh if the transfer had already completed on the server.'
        );
        return;
      }

      const apiErr = extractSafeApiError(err);
      if (apiErr.status === 409) {
        setUploadError(
          apiErr.message ||
            'Cannot upload attachment because this ticket is closed or reached the 20-attachment limit.'
        );
        await onConflict();
      } else if (apiErr.status === 403) {
        setUploadError(apiErr.message || 'You are not authorized to upload attachments to this ticket.');
      } else if (apiErr.status === 404) {
        setUploadError(apiErr.message || 'This ticket could not be found.');
      } else if (apiErr.status === 413) {
        setUploadError(apiErr.message || 'File exceeds the 10 MiB maximum size limit.');
      } else {
        setUploadError(apiErr.message || 'Failed to upload attachment. Please try again.');
      }
    } finally {
      if (uploadAbortRef.current === controller) {
        uploadAbortRef.current = null;
        if (isMountedRef.current && currentTicketIdRef.current === requestTicketId) {
          setIsUploading(false);
        }
      }
    }
  };

  const handleCancelUpload = () => {
    uploadAbortRef.current?.abort();
  };

  const handleDownload = async (attachment: AttachmentResponse) => {
    const requestTicketId = ticketId;
    const controller = new AbortController();
    downloadAbortsRef.current.add(controller);
    setDownloadError(null);

    setDownloadingIds((prev) => {
      const next = new Set(prev);
      next.add(attachment.id);
      return next;
    });

    try {
      const result = await downloadTicketAttachmentApi(
        requestTicketId,
        attachment.id,
        attachment.originalFileName,
        controller.signal
      );

      if (
        !isMountedRef.current ||
        controller.signal.aborted ||
        currentTicketIdRef.current !== requestTicketId
      ) {
        return;
      }

      const objectUrl = URL.createObjectURL(result.blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = result.fileName;
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);

      const timerId = window.setTimeout(() => {
        URL.revokeObjectURL(objectUrl);
        pendingRevokesRef.current.delete(timerId);
      }, DOWNLOAD_REVOKE_DELAY_MS);
      pendingRevokesRef.current.set(timerId, objectUrl);
    } catch (err: unknown) {
      if (
        !isMountedRef.current ||
        currentTicketIdRef.current !== requestTicketId ||
        isCanceledRequest(err, controller)
      ) {
        return;
      }

      const apiErr = extractSafeApiError(err);
      let message = 'Failed to download attachment. Please try again.';
      if (apiErr.status === 403) {
        message = apiErr.message || 'You are not authorized to download this attachment.';
      } else if (apiErr.status === 404) {
        message = apiErr.message || 'This attachment or ticket could no longer be found.';
      } else if (apiErr.message) {
        message = apiErr.message;
      }

      setDownloadError(message);
      toast.error(message);
    } finally {
      downloadAbortsRef.current.delete(controller);
      if (isMountedRef.current && currentTicketIdRef.current === requestTicketId) {
        setDownloadingIds((prev) => {
          const next = new Set(prev);
          next.delete(attachment.id);
          return next;
        });
      }
    }
  };

  const canDeleteAttachment = (attachment: AttachmentResponse): boolean => {
    return !isClosed && (user?.role === 'ADMIN' || attachment.uploadedById === user?.userId);
  };

  const showingFrom =
    attachmentsPage && attachmentsPage.totalElements > 0
      ? attachmentsPage.page * attachmentsPage.size + 1
      : 0;
  const showingTo = attachmentsPage
    ? Math.min((attachmentsPage.page + 1) * attachmentsPage.size, attachmentsPage.totalElements)
    : 0;

  return (
    <div className="space-y-6">
      {/* Status / Quota / Upload Controls */}
      {isClosed ? (
        <div
          role="status"
          className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-600 text-xs flex items-center gap-2.5"
        >
          <Lock className="w-4 h-4 text-slate-500 shrink-0" />
          <span>
            This ticket is closed. Existing attachments can still be viewed and downloaded, but uploading or deleting attachments is disabled.
          </span>
        </div>
      ) : isQuotaReached ? (
        <div
          role="status"
          className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-2.5"
        >
          <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
          <span>
            Maximum limit of {MAX_ATTACHMENTS_PER_TICKET} attachments reached for this ticket. Remove an existing attachment before uploading another.
          </span>
        </div>
      ) : (
        <div className="space-y-3">
          {uploadError && (
            <div
              role="alert"
              className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2"
            >
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{uploadError}</span>
            </div>
          )}

          {uploadNotice && (
            <div
              role="status"
              className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-slate-700 text-xs flex items-center gap-2"
            >
              <Info className="w-4 h-4 text-slate-500 shrink-0" />
              <span>{uploadNotice}</span>
            </div>
          )}

          <div
            onDragOver={(e) => {
              e.preventDefault();
              if (!isUploading) {
                setIsDragging(true);
              }
            }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            className={`p-4 rounded-xl border-2 border-dashed transition-colors ${
              isDragging
                ? 'border-indigo-400 bg-indigo-50/50'
                : 'border-slate-200 bg-slate-50/60'
            }`}
          >
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div className="space-y-1">
                <label
                  htmlFor="attachment-file-input"
                  className="block text-xs font-semibold uppercase tracking-wider text-slate-700"
                >
                  Add Attachment ({totalElements}/{MAX_ATTACHMENTS_PER_TICKET})
                </label>
                <p className="text-xs text-slate-500">
                  Allowed formats: PDF, PNG, JPG, JPEG, TXT (max 10 MiB per file).
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <input
                  ref={fileInputRef}
                  id="attachment-file-input"
                  type="file"
                  accept={ACCEPT_ATTRIBUTE}
                  onChange={handleFileInputChange}
                  disabled={isUploading}
                  className="sr-only"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={isUploading}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Paperclip className="w-3.5 h-3.5 mr-1.5" />
                  Select File
                </Button>

                {selectedFile && !isUploading && (
                  <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    onClick={handleUpload}
                  >
                    <Upload className="w-3.5 h-3.5 mr-1.5" />
                    Upload
                  </Button>
                )}

                {isUploading && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCancelUpload}
                    className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                  >
                    <X className="w-3.5 h-3.5 mr-1" />
                    Cancel
                  </Button>
                )}
              </div>
            </div>

            {selectedFile && (
              <div className="mt-3 pt-3 border-t border-slate-200/80 flex items-center justify-between gap-3 text-xs">
                <div className="flex items-center gap-2 min-w-0">
                  <FileText className="w-4 h-4 text-indigo-600 shrink-0" />
                  <span className="font-medium text-slate-800 truncate">
                    {selectedFile.name}
                  </span>
                  <span className="text-slate-500 shrink-0">
                    ({formatFileSize(selectedFile.size)})
                  </span>
                </div>

                {!isUploading && (
                  <button
                    type="button"
                    onClick={() => {
                      clearSelectedFile();
                      setUploadError(null);
                    }}
                    aria-label="Clear selected file"
                    className="text-slate-400 hover:text-slate-600 p-1 rounded transition-colors shrink-0"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            )}

            {isUploading && (
              <div className="mt-3 space-y-1.5">
                <div className="flex items-center justify-between text-xs text-slate-600">
                  <span>Uploading attachment...</span>
                  <span className="font-mono font-medium">{uploadProgress}%</span>
                </div>
                <div
                  role="progressbar"
                  aria-label="Upload progress"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={uploadProgress}
                  className="h-2 w-full rounded-full bg-slate-200 overflow-hidden"
                >
                  <div
                    className="h-full bg-indigo-600 transition-all duration-150"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Download Error Banner */}
      {downloadError && (
        <div
          role="alert"
          className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center justify-between gap-2"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{downloadError}</span>
          </div>
          <button
            type="button"
            onClick={() => setDownloadError(null)}
            aria-label="Dismiss download error"
            className="text-rose-500 hover:text-rose-700 p-0.5 rounded"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Attachments List States */}
      {isLoading ? (
        <div className="space-y-3 animate-pulse" aria-label="Loading attachments">
          {Array.from({ length: 3 }).map((_, idx) => (
            <div
              key={idx}
              className="p-4 rounded-xl bg-slate-100 border border-slate-200/60 flex items-center justify-between gap-4"
            >
              <div className="space-y-2 flex-1">
                <div className="h-4 w-48 bg-slate-200 rounded" />
                <div className="h-3 w-64 bg-slate-200 rounded" />
              </div>
              <div className="h-8 w-24 bg-slate-200 rounded-lg" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div
          role="alert"
          className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 flex items-center justify-between gap-3 text-xs"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
            Retry
          </Button>
        </div>
      ) : attachments.length === 0 ? (
        <div className="p-8 text-center bg-slate-50 rounded-xl border border-dashed border-slate-200 text-slate-500 space-y-2">
          <Paperclip className="w-8 h-8 mx-auto text-slate-400 stroke-1" />
          <p className="text-sm font-medium text-slate-700">No attachments yet</p>
          <p className="text-xs text-slate-500">
            {isClosed
              ? 'No files were attached to this ticket before it was closed.'
              : 'Upload supporting documents, logs, or screenshots above.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {attachments.map((attachment) => {
            const isOwnUpload = attachment.uploadedById === user?.userId;
            const isDownloading = downloadingIds.has(attachment.id);

            return (
              <div
                key={attachment.id}
                className="p-4 rounded-xl border border-slate-200/80 bg-white shadow-xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"
              >
                <div className="flex items-start gap-3 min-w-0">
                  <div className="p-2 rounded-lg bg-indigo-50 shrink-0 mt-0.5">
                    {renderFileIcon(attachment.contentType, attachment.originalFileName)}
                  </div>

                  <div className="min-w-0 space-y-1">
                    <p className="text-sm font-semibold text-slate-900 break-all">
                      {attachment.originalFileName}
                    </p>
                    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-500">
                      <span className="font-medium text-slate-700">
                        {attachment.uploadedByName}
                      </span>
                      {isOwnUpload && (
                        <span className="px-1.5 py-0.2 rounded text-[10px] font-semibold bg-indigo-100 text-indigo-700 border border-indigo-200/60">
                          You
                        </span>
                      )}
                      <span aria-hidden="true">•</span>
                      <span>{formatFileSize(attachment.fileSize)}</span>
                      <span aria-hidden="true">•</span>
                      <span>{formatUploadDate(attachment.createdAt)}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    isLoading={isDownloading}
                    disabled={isDownloading}
                    onClick={() => handleDownload(attachment)}
                    aria-label={`Download ${attachment.originalFileName}`}
                  >
                    {!isDownloading && <Download className="w-3.5 h-3.5 mr-1.5" />}
                    {isDownloading ? 'Downloading...' : 'Download'}
                  </Button>

                  {canDeleteAttachment(attachment) && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setAttachmentToDelete(attachment)}
                      aria-label={`Delete ${attachment.originalFileName}`}
                      className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                    >
                      <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                      Delete
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Pagination Footer (shown only when totalPages > 1) */}
      {attachmentsPage && attachmentsPage.totalPages > 1 && (
        <div className="p-3.5 rounded-xl bg-slate-50/70 border border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
          <div>
            Showing <span className="font-semibold text-slate-700">{showingFrom}</span> to{' '}
            <span className="font-semibold text-slate-700">{showingTo}</span> of{' '}
            <span className="font-semibold text-slate-700">{attachmentsPage.totalElements}</span>{' '}
            attachments
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1.5">
              <label htmlFor="attachments-page-size">Rows:</label>
              <select
                id="attachments-page-size"
                value={size}
                onChange={(e) => onSizeChange(Number(e.target.value))}
                disabled={isLoading}
                className="px-2 py-1 rounded border border-slate-300 bg-white text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                {PAGE_SIZE_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-1">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={attachmentsPage.first || isLoading}
                onClick={() => onPageChange(Math.max(0, page - 1))}
                aria-label="Previous page"
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <span className="px-2 font-medium text-slate-700">
                Page {attachmentsPage.page + 1} of {attachmentsPage.totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={attachmentsPage.last || isLoading}
                onClick={() => onPageChange(Math.min(attachmentsPage.totalPages - 1, page + 1))}
                aria-label="Next page"
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Attachment Deletion Confirmation Modal */}
      <DeleteAttachmentModal
        ticketId={ticketId}
        attachment={attachmentToDelete}
        isOpen={attachmentToDelete !== null}
        onClose={() => setAttachmentToDelete(null)}
        onDeleted={onDeleteSuccess}
        onConflict={onConflict}
      />
    </div>
  );
}

