import { describe, expect, it, afterEach, beforeEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor, act } from '@testing-library/react';
import { AttachmentsPanel } from '@/components/tickets/AttachmentsPanel';
import { DeleteAttachmentModal } from '@/components/tickets/DeleteAttachmentModal';
import * as useAuthHook from '@/hooks/useAuth';
import * as attachmentsApi from '@/lib/api/attachments';
import type {
  AttachmentDownloadResult,
  AttachmentPageResponse,
  AttachmentResponse,
} from '@/types/attachment.types';
import type { AuthUser } from '@/types/auth.types';
import { toast } from 'sonner';

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

vi.mock('@/hooks/useAuth');
vi.mock('@/lib/api/attachments');
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

describe('AttachmentsPanel', () => {
  const customerUser: AuthUser = {
    userId: 10,
    name: 'Jane Customer',
    email: 'jane@example.com',
    role: 'CUSTOMER',
  };

  const agentUser: AuthUser = {
    userId: 20,
    name: 'Alex Agent',
    email: 'agent@support.ai',
    role: 'AGENT',
  };

  const adminUser: AuthUser = {
    userId: 1,
    name: 'Admin Chief',
    email: 'admin@support.ai',
    role: 'ADMIN',
  };

  const ownAttachment: AttachmentResponse = {
    id: 101,
    ticketId: 5,
    uploadedById: 10,
    uploadedByName: 'Jane Customer',
    originalFileName: 'error-screenshot.png',
    contentType: 'image/png',
    fileSize: 2048,
    createdAt: '2026-11-10T10:15:00Z',
  };

  const otherAttachment: AttachmentResponse = {
    id: 102,
    ticketId: 5,
    uploadedById: 20,
    uploadedByName: 'Alex Agent',
    originalFileName: 'diagnostic-report.pdf',
    contentType: 'application/pdf',
    fileSize: 1572864,
    createdAt: '2026-11-10T11:30:00Z',
  };

  const defaultPage: AttachmentPageResponse = {
    content: [ownAttachment, otherAttachment],
    page: 0,
    size: 10,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
  };

  const mockAuth = (user: AuthUser) => {
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user,
      token: 'jwt-token',
      isAuthenticated: true,
      isInitializing: false,
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });
  };

  const renderPanel = (
    overrides: Partial<React.ComponentProps<typeof AttachmentsPanel>> = {},
    user: AuthUser = customerUser
  ) => {
    mockAuth(user);
    const props: React.ComponentProps<typeof AttachmentsPanel> = {
      ticketId: 5,
      ticketStatus: 'OPEN',
      attachmentsPage: defaultPage,
      page: 0,
      size: 10,
      isLoading: false,
      error: null,
      onPageChange: vi.fn(),
      onSizeChange: vi.fn(),
      onRetry: vi.fn(),
      onUploadSuccess: vi.fn(),
      onDeleteSuccess: vi.fn(),
      onConflict: vi.fn(),
      ...overrides,
    };
    const result = render(<AttachmentsPanel {...props} />);
    return { ...result, props };
  };

  const originalCreateObjectURL = Object.getOwnPropertyDescriptor(URL, 'createObjectURL');
  const originalRevokeObjectURL = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL');

  const stubObjectUrlApi = (initialUrl = 'blob:http://localhost/temp-url') => {
    const createMock = vi.fn().mockReturnValue(initialUrl);
    const revokeMock = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: createMock,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: revokeMock,
    });
    return { createMock, revokeMock };
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
    if (originalCreateObjectURL) {
      Object.defineProperty(URL, 'createObjectURL', originalCreateObjectURL);
    } else {
      delete (URL as unknown as Record<string, unknown>).createObjectURL;
    }
    if (originalRevokeObjectURL) {
      Object.defineProperty(URL, 'revokeObjectURL', originalRevokeObjectURL);
    } else {
      delete (URL as unknown as Record<string, unknown>).revokeObjectURL;
    }
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('renders attachment metadata and shows "You" indicator only on current user uploads', () => {
    renderPanel();

    expect(screen.getByText('error-screenshot.png')).toBeInTheDocument();
    expect(screen.getByText('diagnostic-report.pdf')).toBeInTheDocument();
    expect(screen.getByText('Jane Customer')).toBeInTheDocument();
    expect(screen.getByText('Alex Agent')).toBeInTheDocument();
    expect(screen.getByText('2.0 KB')).toBeInTheDocument();
    expect(screen.getByText('1.50 MB')).toBeInTheDocument();

    const youBadges = screen.getAllByText('You');
    expect(youBadges).toHaveLength(1);
  });

  it('renders loading, empty, and fetch-error states with retry', () => {
    const { rerender, props } = renderPanel({
      attachmentsPage: null,
      isLoading: true,
    });

    expect(screen.getByLabelText('Loading attachments')).toBeInTheDocument();

    rerender(
      <AttachmentsPanel
        {...props}
        isLoading={false}
        attachmentsPage={{
          content: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        }}
      />
    );
    expect(screen.getByText('No attachments yet')).toBeInTheDocument();

    const onRetry = vi.fn();
    rerender(
      <AttachmentsPanel
        {...props}
        isLoading={false}
        error="Failed to load attachments."
        onRetry={onRetry}
      />
    );

    expect(screen.getByText('Failed to load attachments.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Retry/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('validates file extension, empty files, and 10 MiB max size before upload', () => {
    renderPanel();

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;

    const badExt = new File(['abc'], 'script.exe', { type: 'application/octet-stream' });
    fireEvent.change(fileInput, { target: { files: [badExt] } });
    expect(
      screen.getByText(/Unsupported file type\. Allowed formats: PDF, PNG, JPG, JPEG, TXT\./i)
    ).toBeInTheDocument();

    const emptyFile = new File([], 'empty.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [emptyFile] } });
    expect(screen.getByText(/File is empty/i)).toBeInTheDocument();

    const oversizedFile = new File(['x'], 'large.pdf', { type: 'application/pdf' });
    Object.defineProperty(oversizedFile, 'size', { value: 10_485_761 });
    fireEvent.change(fileInput, { target: { files: [oversizedFile] } });
    expect(screen.getByText(/File exceeds the 10 MiB/i)).toBeInTheDocument();

    expect(attachmentsApi.uploadTicketAttachmentApi).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /^Upload$/i })).not.toBeInTheDocument();
  });

  it('performs two-step upload, displays progress bar, and refreshes on success', async () => {
    const onUploadSuccess = vi.fn().mockResolvedValue(undefined);
    const uploadSpy = vi
      .spyOn(attachmentsApi, 'uploadTicketAttachmentApi')
      .mockImplementation(async (req) => {
        req.onUploadProgress?.(45);
        await new Promise((r) => setTimeout(r, 15));
        req.onUploadProgress?.(100);
        return {
          id: 103,
          ticketId: 5,
          uploadedById: 10,
          uploadedByName: 'Jane Customer',
          originalFileName: 'invoice.pdf',
          contentType: 'application/pdf',
          fileSize: 512,
          createdAt: '2026-11-12T08:00:00Z',
        };
      });

    renderPanel({ onUploadSuccess });

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    const validFile = new File(['%PDF-1.4'], 'invoice.pdf', { type: 'application/pdf' });

    // Step 1: Select file (must NOT upload automatically)
    fireEvent.change(fileInput, { target: { files: [validFile] } });
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument();
    expect(uploadSpy).not.toHaveBeenCalled();

    // Step 2: Explicitly click Upload
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    await waitFor(() => {
      const progressbar = screen.getByRole('progressbar', { name: /Upload progress/i });
      expect(progressbar).toHaveAttribute('aria-valuenow', '45');
    });

    await waitFor(() => {
      expect(uploadSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          ticketId: 5,
          file: validFile,
        })
      );
      expect(toast.success).toHaveBeenCalledWith('Uploaded "invoice.pdf".');
      expect(onUploadSuccess).toHaveBeenCalledTimes(1);
    });
  });

  it('aborts in-flight upload when Cancel is clicked without claiming server cancellation', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi').mockImplementation(
      (req) =>
        new Promise((_resolve, reject) => {
          capturedSignal = req.signal;
          req.signal?.addEventListener('abort', () => {
            const abortErr = new Error('canceled');
            abortErr.name = 'CanceledError';
            reject(abortErr);
          });
        })
    );

    renderPanel();

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    const validFile = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [validFile] } });
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    const cancelBtn = await screen.findByRole('button', { name: /Cancel/i });
    fireEvent.click(cancelBtn);

    await waitFor(() => {
      expect(capturedSignal?.aborted).toBe(true);
      expect(
        screen.getByText(/Upload request was canceled in your browser/i)
      ).toBeInTheDocument();
    });
  });

  it('handles 403, 404, 409, and network errors safely during upload and invokes onConflict on 409', async () => {
    const onConflict = vi.fn().mockResolvedValue(undefined);
    const uploadSpy = vi
      .spyOn(attachmentsApi, 'uploadTicketAttachmentApi')
      .mockRejectedValueOnce({ status: 403, message: 'Forbidden ticket access' })
      .mockRejectedValueOnce({ status: 404, message: 'Ticket not found' })
      .mockRejectedValueOnce({ status: 409, message: 'Cannot upload attachments to a closed ticket' })
      .mockRejectedValueOnce({ status: 500, message: 'Network unavailable' });

    renderPanel({ onConflict });
    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    const validFile = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [validFile] } });

    // 403
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));
    expect(await screen.findByText('Forbidden ticket access')).toBeInTheDocument();

    // 404
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));
    expect(await screen.findByText('Ticket not found')).toBeInTheDocument();

    // 409
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));
    await waitFor(() => {
      expect(screen.getByText('Cannot upload attachments to a closed ticket')).toBeInTheDocument();
      expect(onConflict).toHaveBeenCalledTimes(1);
    });

    // 500 network error
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));
    expect(await screen.findByText('Network unavailable')).toBeInTheDocument();
    expect(uploadSpy).toHaveBeenCalledTimes(4);
  });

  it('downloads using API sanitized filename, creates object URL, and revokes after deferred delay', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });

    const { createMock, revokeMock } = stubObjectUrlApi('blob:http://localhost/temp-123');
    const anchorClickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {});

    const blob = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    vi.spyOn(attachmentsApi, 'downloadTicketAttachmentApi').mockResolvedValue({
      blob,
      fileName: 'sanitized-report.pdf',
      contentType: 'application/pdf',
    });

    const appendSpy = vi.spyOn(document.body, 'appendChild');
    renderPanel();

    fireEvent.click(screen.getByRole('button', { name: 'Download diagnostic-report.pdf' }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith(blob);
      expect(anchorClickSpy).toHaveBeenCalledTimes(1);
    });

    const clickedAnchor = appendSpy.mock.calls.find(
      ([node]) => node instanceof HTMLAnchorElement
    )?.[0] as HTMLAnchorElement | undefined;
    expect(clickedAnchor?.download).toBe('sanitized-report.pdf');
    expect(clickedAnchor?.href).toBe('blob:http://localhost/temp-123');

    // Should not revoke immediately before browser download initiates
    expect(revokeMock).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(revokeMock).toHaveBeenCalledWith('blob:http://localhost/temp-123');
  });

  it('revokes pending object URLs immediately on unmount', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });

    const { createMock, revokeMock } = stubObjectUrlApi('blob:http://localhost/unmount-url');
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.spyOn(attachmentsApi, 'downloadTicketAttachmentApi').mockResolvedValue({
      blob: new Blob(['abc'], { type: 'text/plain' }),
      fileName: 'error-screenshot.png',
      contentType: 'image/png',
    });

    const { unmount } = renderPanel();
    fireEvent.click(screen.getByRole('button', { name: 'Download error-screenshot.png' }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledTimes(1);
    });
    expect(revokeMock).not.toHaveBeenCalled();

    unmount();
    expect(revokeMock).toHaveBeenCalledWith('blob:http://localhost/unmount-url');
  });

  it('displays safe error message on download failure without exposing raw Blob content', async () => {
    vi.spyOn(attachmentsApi, 'downloadTicketAttachmentApi').mockRejectedValue({
      status: 404,
      message: 'Attachment not found with ID: 102',
    });

    renderPanel();
    fireEvent.click(screen.getByRole('button', { name: 'Download diagnostic-report.pdf' }));

    await waitFor(() => {
      expect(screen.getByText('Attachment not found with ID: 102')).toBeInTheDocument();
      expect(toast.error).toHaveBeenCalledWith('Attachment not found with ID: 102');
    });
  });

  it('enforces delete button visibility by role and uploader ownership and handles delete confirmation modal errors', async () => {
    const onDeleteSuccess = vi.fn().mockResolvedValue(undefined);
    const onConflict = vi.fn().mockResolvedValue(undefined);

    // 1. CUSTOMER: can delete own (101), not other (102)
    const { rerender, props } = renderPanel({ onDeleteSuccess, onConflict }, customerUser);
    expect(screen.getByRole('button', { name: 'Delete error-screenshot.png' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete diagnostic-report.pdf' })).not.toBeInTheDocument();

    // 2. AGENT: can delete own (102), not customer's (101)
    mockAuth(agentUser);
    rerender(<AttachmentsPanel {...props} />);
    expect(screen.queryByRole('button', { name: 'Delete error-screenshot.png' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete diagnostic-report.pdf' })).toBeInTheDocument();

    // 3. ADMIN: can delete ANY attachment
    mockAuth(adminUser);
    rerender(<AttachmentsPanel {...props} />);
    expect(screen.getByRole('button', { name: 'Delete error-screenshot.png' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete diagnostic-report.pdf' })).toBeInTheDocument();

    // Open delete modal and test 409 + success
    const deleteSpy = vi
      .spyOn(attachmentsApi, 'deleteTicketAttachmentApi')
      .mockRejectedValueOnce({ status: 409, message: 'Cannot delete attachments from a closed ticket' })
      .mockResolvedValueOnce(undefined);

    fireEvent.click(screen.getByRole('button', { name: 'Delete error-screenshot.png' }));
    expect(screen.getByRole('heading', { name: 'Delete Attachment' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));
    await waitFor(() => {
      expect(
        screen.getByText('Cannot delete attachments from a closed ticket')
      ).toBeInTheDocument();
      expect(onConflict).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));
    await waitFor(() => {
      expect(deleteSpy).toHaveBeenCalledWith(5, 101);
      expect(onDeleteSuccess).toHaveBeenCalledTimes(1);
      expect(toast.success).toHaveBeenCalledWith('Attachment "error-screenshot.png" deleted.');
    });
  });

  it('hides upload and delete controls on CLOSED tickets while keeping listing and download enabled', () => {
    renderPanel({ ticketStatus: 'CLOSED' }, adminUser);

    expect(
      screen.getByText(/This ticket is closed\. Existing attachments can still be viewed and downloaded/i)
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Select File/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete/i })).not.toBeInTheDocument();

    expect(screen.getByRole('button', { name: 'Download error-screenshot.png' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Download diagnostic-report.pdf' })).toBeEnabled();
  });

  it('replaces upload controls with quota notice when totalElements reaches 20', () => {
    renderPanel({
      attachmentsPage: {
        ...defaultPage,
        totalElements: 20,
        totalPages: 2,
        last: false,
      },
    });

    expect(
      screen.getByText(/Maximum limit of 20 attachments reached for this ticket/i)
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Select File/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download error-screenshot.png' })).toBeEnabled();
  });

  it('shows pagination only when totalPages > 1, supports page/size controls, and recovers from empty last page', async () => {
    const onPageChange = vi.fn();
    const onSizeChange = vi.fn();

    const { rerender, props } = renderPanel({
      onPageChange,
      onSizeChange,
      attachmentsPage: {
        ...defaultPage,
        totalElements: 2,
        totalPages: 1,
      },
    });
    expect(screen.queryByLabelText('Next page')).not.toBeInTheDocument();

    rerender(
      <AttachmentsPanel
        {...props}
        page={0}
        size={10}
        attachmentsPage={{
          content: [ownAttachment],
          page: 0,
          size: 10,
          totalElements: 12,
          totalPages: 2,
          first: true,
          last: false,
        }}
      />
    );

    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(onPageChange).toHaveBeenCalledWith(1);

    fireEvent.change(screen.getByLabelText('Rows:'), { target: { value: '5' } });
    expect(onSizeChange).toHaveBeenCalledWith(5);

    // Empty last page after deletion recovery
    rerender(
      <AttachmentsPanel
        {...props}
        page={1}
        size={10}
        attachmentsPage={{
          content: [],
          page: 1,
          size: 10,
          totalElements: 10,
          totalPages: 1,
          first: false,
          last: true,
        }}
      />
    );

    await waitFor(() => {
      expect(onPageChange).toHaveBeenCalledWith(0);
    });
  });

  it('supports drag-and-drop file selection and clearing a selected file before upload', () => {
    const uploadSpy = vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi');
    renderPanel();

    const dropzoneHeading = screen.getByText(/Add Attachment \(2\/20\)/i);
    const dropzone = dropzoneHeading.closest('div[class*="border-dashed"]') as HTMLElement;
    expect(dropzone).not.toBeNull();

    const droppedFile = new File(['hello'], 'dropped-log.txt', { type: 'text/plain' });
    fireEvent.dragOver(dropzone, { dataTransfer: { files: [droppedFile] } });
    fireEvent.drop(dropzone, { dataTransfer: { files: [droppedFile] } });

    expect(screen.getByText('dropped-log.txt')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Upload$/i })).toBeInTheDocument();
    expect(uploadSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Clear selected file' }));
    expect(screen.queryByText('dropped-log.txt')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Upload$/i })).not.toBeInTheDocument();
    expect(uploadSpy).not.toHaveBeenCalled();
  });

  it('displays inline error on HTTP 413 payload-too-large upload rejection', async () => {
    const onUploadSuccess = vi.fn().mockResolvedValue(undefined);
    const onConflict = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi').mockRejectedValueOnce({
      status: 413,
      message: 'Attachment payload exceeds the 10 MiB limit.',
    });

    renderPanel({ onUploadSuccess, onConflict });

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    const validFile = new File(['%PDF-1.4'], 'huge-scan.pdf', { type: 'application/pdf' });
    fireEvent.change(fileInput, { target: { files: [validFile] } });
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    expect(
      await screen.findByText('Attachment payload exceeds the 10 MiB limit.')
    ).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
    expect(onUploadSuccess).not.toHaveBeenCalled();
    expect(onConflict).not.toHaveBeenCalled();
  });

  it('supports Previous page navigation and page-size selection of 20', () => {
    const onPageChange = vi.fn();
    const onSizeChange = vi.fn();

    renderPanel({
      page: 1,
      size: 10,
      onPageChange,
      onSizeChange,
      attachmentsPage: {
        content: [ownAttachment],
        page: 1,
        size: 10,
        totalElements: 25,
        totalPages: 3,
        first: false,
        last: false,
      },
    });

    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(onPageChange).toHaveBeenCalledWith(0);

    fireEvent.change(screen.getByLabelText('Rows:'), { target: { value: '20' } });
    expect(onSizeChange).toHaveBeenCalledWith(20);
  });

  it('ignores late upload resolution if the request was already aborted', async () => {
    const deferred = createDeferred<AttachmentResponse>();
    const onUploadSuccess = vi.fn().mockResolvedValue(undefined);
    let capturedSignal: AbortSignal | undefined;

    vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi').mockImplementation((req) => {
      capturedSignal = req.signal;
      return deferred.promise;
    });

    renderPanel({ onUploadSuccess });

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    const validFile = new File(['notes'], 'late-upload.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [validFile] } });
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    const cancelBtn = await screen.findByRole('button', { name: /Cancel/i });
    fireEvent.click(cancelBtn);
    expect(capturedSignal?.aborted).toBe(true);

    await act(async () => {
      deferred.resolve({
        ...ownAttachment,
        id: 199,
        originalFileName: 'late-upload.txt',
      });
      await deferred.promise;
    });

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /Cancel/i })).not.toBeInTheDocument();
    });
    expect(toast.success).not.toHaveBeenCalled();
    expect(onUploadSuccess).not.toHaveBeenCalled();
    expect(
      screen.getByText(/Upload request was canceled in your browser/i)
    ).toBeInTheDocument();
  });

  it('aborts active downloads when ticketId changes without aborting new ticket downloads', async () => {
    const { createMock } = stubObjectUrlApi('blob:http://localhost/ticket-6-url');
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const firstDownload = createDeferred<AttachmentDownloadResult>();
    const secondDownload = createDeferred<AttachmentDownloadResult>();
    const capturedSignals: AbortSignal[] = [];

    vi.spyOn(attachmentsApi, 'downloadTicketAttachmentApi')
      .mockImplementationOnce((_ticketId, _attId, _fileName, signal) => {
        if (signal) capturedSignals.push(signal);
        return firstDownload.promise;
      })
      .mockImplementationOnce((_ticketId, _attId, _fileName, signal) => {
        if (signal) capturedSignals.push(signal);
        return secondDownload.promise;
      });

    const ticketSixAttachment: AttachmentResponse = {
      ...ownAttachment,
      id: 201,
      ticketId: 6,
      originalFileName: 'ticket-6-log.txt',
    };
    const ticketSixPage: AttachmentPageResponse = {
      content: [ticketSixAttachment],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    };

    const { rerender, props } = renderPanel({ ticketId: 5 });

    const ticketFiveBtn = screen.getByRole('button', { name: 'Download error-screenshot.png' });
    fireEvent.click(ticketFiveBtn);

    expect(capturedSignals).toHaveLength(1);
    expect(capturedSignals[0].aborted).toBe(false);
    expect(ticketFiveBtn).toBeDisabled();

    // Switch to ticket 6 while ticket 5 download is still pending
    rerender(<AttachmentsPanel {...props} ticketId={6} attachmentsPage={ticketSixPage} />);

    expect(capturedSignals[0].aborted).toBe(true);

    // Resolving the aborted ticket 5 download must not create an object URL or error on ticket 6
    await act(async () => {
      firstDownload.resolve({
        blob: new Blob(['stale'], { type: 'image/png' }),
        fileName: 'error-screenshot.png',
        contentType: 'image/png',
      });
      await firstDownload.promise;
    });
    expect(createMock).not.toHaveBeenCalled();
    expect(toast.error).not.toHaveBeenCalled();

    // Starting a download on ticket 6 must keep its signal un-aborted and complete normally
    const ticketSixBtn = screen.getByRole('button', { name: 'Download ticket-6-log.txt' });
    expect(ticketSixBtn).toBeEnabled();
    fireEvent.click(ticketSixBtn);

    expect(capturedSignals).toHaveLength(2);
    expect(capturedSignals[1].aborted).toBe(false);

    await act(async () => {
      secondDownload.resolve({
        blob: new Blob(['ticket 6'], { type: 'text/plain' }),
        fileName: 'ticket-6-log.txt',
        contentType: 'text/plain',
      });
      await secondDownload.promise;
    });

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledTimes(1);
      expect(screen.getByRole('button', { name: 'Download ticket-6-log.txt' })).toBeEnabled();
    });
  });
});

describe('DeleteAttachmentModal', () => {
  const sampleAttachment: AttachmentResponse = {
    id: 101,
    ticketId: 5,
    uploadedById: 10,
    uploadedByName: 'Jane Customer',
    originalFileName: 'error-screenshot.png',
    contentType: 'image/png',
    fileSize: 2048,
    createdAt: '2026-11-10T10:15:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it('displays error feedback for HTTP 403, HTTP 404, and generic network errors', async () => {
    const onClose = vi.fn();
    const onDeleted = vi.fn().mockResolvedValue(undefined);
    const deleteSpy = vi
      .spyOn(attachmentsApi, 'deleteTicketAttachmentApi')
      .mockRejectedValueOnce({ status: 403, message: 'Permission denied for deletion.' })
      .mockRejectedValueOnce({ status: 404, message: 'Attachment no longer exists.' })
      .mockRejectedValueOnce(new Error('Network connection lost'));

    render(
      <DeleteAttachmentModal
        ticketId={5}
        attachment={sampleAttachment}
        isOpen={true}
        onClose={onClose}
        onDeleted={onDeleted}
      />
    );

    // 403
    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));
    expect(await screen.findByText('Permission denied for deletion.')).toBeInTheDocument();

    // 404
    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));
    expect(await screen.findByText('Attachment no longer exists.')).toBeInTheDocument();

    // Generic network error
    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));
    expect(await screen.findByText('Network connection lost')).toBeInTheDocument();

    expect(deleteSpy).toHaveBeenCalledTimes(3);
    expect(onClose).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('invokes onClose without calling delete API when Cancel is clicked', () => {
    const deleteSpy = vi.spyOn(attachmentsApi, 'deleteTicketAttachmentApi');
    const onClose = vi.fn();
    const onDeleted = vi.fn();

    render(
      <DeleteAttachmentModal
        ticketId={5}
        attachment={sampleAttachment}
        isOpen={true}
        onClose={onClose}
        onDeleted={onDeleted}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(deleteSpy).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
  });

  it('does not trigger toast, onClose, or onDeleted when unmounted before delete resolves', async () => {
    const deferred = createDeferred<void>();
    vi.spyOn(attachmentsApi, 'deleteTicketAttachmentApi').mockReturnValueOnce(deferred.promise);

    const onClose = vi.fn();
    const onDeleted = vi.fn().mockResolvedValue(undefined);

    const { unmount } = render(
      <DeleteAttachmentModal
        ticketId={5}
        attachment={sampleAttachment}
        isOpen={true}
        onClose={onClose}
        onDeleted={onDeleted}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));
    unmount();

    await act(async () => {
      deferred.resolve(undefined);
      await deferred.promise;
    });

    expect(toast.success).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
  });
});

