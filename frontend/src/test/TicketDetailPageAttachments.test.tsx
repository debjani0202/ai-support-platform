import { describe, expect, it, afterEach, beforeEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useNavigate } from 'react-router-dom';
import { TicketDetailPage } from '@/pages/tickets/TicketDetailPage';
import * as useAuthHook from '@/hooks/useAuth';
import * as ticketsApi from '@/lib/api/tickets';
import * as commentsApi from '@/lib/api/comments';
import * as auditApi from '@/lib/api/audit';
import * as attachmentsApi from '@/lib/api/attachments';
import type { TicketResponse } from '@/types/ticket.types';
import type { AttachmentPageResponse, AttachmentResponse } from '@/types/attachment.types';

vi.mock('@/hooks/useAuth');
vi.mock('@/lib/api/tickets');
vi.mock('@/lib/api/comments');
vi.mock('@/lib/api/audit');
vi.mock('@/lib/api/attachments');
vi.mock('@/components/notifications/NotificationBell', () => ({
  NotificationBell: () => null,
}));
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function TicketSwitcherHarness({ nextTicketId }: { nextTicketId: number }) {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(`/tickets/${nextTicketId}`)}>
      Switch to ticket {nextTicketId}
    </button>
  );
}

describe('TicketDetailPage — Attachments Integration', () => {
  const customerUser = {
    userId: 10,
    name: 'Jane Customer',
    email: 'jane@example.com',
    role: 'CUSTOMER' as const,
  };

  const ticketOne: TicketResponse = {
    id: 1,
    title: 'Billing invoice discrepancy',
    description: 'Duplicate charge on invoice #44',
    status: 'OPEN',
    category: 'PAYMENT',
    priority: 'HIGH',
    createdBy: 10,
    assignedTo: 20,
    createdAt: '2026-11-10T09:00:00Z',
    updatedAt: '2026-11-10T09:30:00Z',
  };

  const ticketTwo: TicketResponse = {
    id: 2,
    title: 'VPN login failure',
    description: 'Cannot connect after password rotation',
    status: 'IN_PROGRESS',
    category: 'ACCOUNT_ACCESS',
    priority: 'MEDIUM',
    createdBy: 10,
    assignedTo: 20,
    createdAt: '2026-11-11T10:00:00Z',
    updatedAt: '2026-11-11T10:15:00Z',
  };

  const sampleAttachment: AttachmentResponse = {
    id: 501,
    ticketId: 1,
    uploadedById: 10,
    uploadedByName: 'Jane Customer',
    originalFileName: 'invoice-april.pdf',
    contentType: 'application/pdf',
    fileSize: 4096,
    createdAt: '2026-11-10T10:00:00Z',
  };

  const pageOneResponse: AttachmentPageResponse = {
    content: [sampleAttachment],
    page: 0,
    size: 10,
    totalElements: 3,
    totalPages: 1,
    first: true,
    last: true,
  };

  const renderPage = (initialRoute = '/tickets/1', nextTicketId?: number) => {
    return render(
      <MemoryRouter initialEntries={[initialRoute]}>
        {nextTicketId !== undefined && <TicketSwitcherHarness nextTicketId={nextTicketId} />}
        <Routes>
          <Route path="/tickets/:id" element={<TicketDetailPage />} />
          <Route path="/tickets" element={<div>Tickets List Route</div>} />
        </Routes>
      </MemoryRouter>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user: customerUser,
      token: 'jwt-token',
      isAuthenticated: true,
      isInitializing: false,
      login: vi.fn(),
      logout: vi.fn(),
      refreshSession: vi.fn(),
    });
    vi.spyOn(commentsApi, 'getTicketCommentsApi').mockResolvedValue([]);
    vi.spyOn(auditApi, 'getTicketAuditLogsApi').mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it('pre-fetches initial attachment page on ticket load, populates tab count, and avoids duplicate fetch on tab click', async () => {
    vi.spyOn(ticketsApi, 'getTicketByIdApi').mockResolvedValue(ticketOne);
    const getAttachmentsSpy = vi
      .spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      .mockResolvedValue(pageOneResponse);

    renderPage('/tickets/1');

    // Badge populates from totalElements before clicking Attachments tab
    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => {
      expect(getAttachmentsSpy).toHaveBeenCalledTimes(1);
      expect(getAttachmentsSpy).toHaveBeenCalledWith(1, { page: 0, size: 10 });
      expect(attachmentsTab).toHaveTextContent('3');
    });

    // Switch to Attachments tab — should render pre-fetched data with no duplicate page-0 fetch
    fireEvent.click(attachmentsTab);
    expect(screen.getByText('invoice-april.pdf')).toBeInTheDocument();
    expect(getAttachmentsSpy).toHaveBeenCalledTimes(1);
  });

  it('resets attachment state when navigating between ticket IDs', async () => {
    const getTicketSpy = vi
      .spyOn(ticketsApi, 'getTicketByIdApi')
      .mockImplementation(async (ticketId) => (ticketId === 2 ? ticketTwo : ticketOne));

    const getAttachmentsSpy = vi
      .spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      .mockImplementation(async (ticketId) => {
        if (ticketId === 2) {
          return {
            content: [
              {
                ...sampleAttachment,
                id: 901,
                ticketId: 2,
                originalFileName: 'vpn-debug.log.txt',
              },
            ],
            page: 0,
            size: 10,
            totalElements: 1,
            totalPages: 1,
            first: true,
            last: true,
          };
        }
        return pageOneResponse;
      });

    renderPage('/tickets/1', 2);

    const attachmentsTabFirst = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => {
      expect(attachmentsTabFirst).toHaveTextContent('3');
    });
    fireEvent.click(attachmentsTabFirst);
    expect(screen.getByText('invoice-april.pdf')).toBeInTheDocument();

    // Navigate to ticket #2
    fireEvent.click(screen.getByRole('button', { name: 'Switch to ticket 2' }));

    await waitFor(() => {
      expect(getTicketSpy).toHaveBeenCalledWith(2);
      expect(getAttachmentsSpy).toHaveBeenCalledWith(2, { page: 0, size: 10 });
      expect(screen.getByRole('button', { name: /Attachments/i })).toHaveTextContent('1');
      expect(screen.getByText('vpn-debug.log.txt')).toBeInTheDocument();
    });
    expect(screen.queryByText('invoice-april.pdf')).not.toBeInTheDocument();
  });

  it('ignores stale attachment page responses when switching pages quickly', async () => {
    vi.spyOn(ticketsApi, 'getTicketByIdApi').mockResolvedValue(ticketOne);

    const deferredPage1 = createDeferred<AttachmentPageResponse>();
    const deferredPage2 = createDeferred<AttachmentPageResponse>();

    vi.spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 25,
        totalPages: 3,
        first: true,
        last: false,
      })
      .mockReturnValueOnce(deferredPage1.promise)
      .mockReturnValueOnce(deferredPage2.promise);

    renderPage('/tickets/1');

    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => expect(attachmentsTab).toHaveTextContent('25'));
    fireEvent.click(attachmentsTab);

    // Request page 1 (slow), then immediately request page 2 (faster)
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() => {
      expect(screen.getByLabelText('Loading attachments')).toBeInTheDocument();
    });

    // Trigger page 2 while page 1 is still loading — let's enable via size change or page 2
    // Change page size to 5 while page 1 fetch is still pending
    fireEvent.change(screen.getByLabelText('Rows:'), { target: { value: '5' } });

    // Newer response (size 5 -> page 0) resolves first
    deferredPage2.resolve({
      content: [
        {
          ...sampleAttachment,
          id: 777,
          originalFileName: 'newer-size-5-file.pdf',
        },
      ],
      page: 0,
      size: 5,
      totalElements: 25,
      totalPages: 5,
      first: true,
      last: false,
    });

    await waitFor(() => {
      expect(screen.getByText('newer-size-5-file.pdf')).toBeInTheDocument();
    });

    // Stale page 1 response finishes afterwards — must be ignored
    deferredPage1.resolve({
      content: [
        {
          ...sampleAttachment,
          id: 666,
          originalFileName: 'stale-page-1-file.pdf',
        },
      ],
      page: 1,
      size: 10,
      totalElements: 25,
      totalPages: 3,
      first: false,
      last: false,
    });

    await waitFor(() => {
      expect(screen.getByText('newer-size-5-file.pdf')).toBeInTheDocument();
    });
    expect(screen.queryByText('stale-page-1-file.pdf')).not.toBeInTheDocument();
  });

  it('refreshes attachment count and Audit Trail after successful upload and deletion', async () => {
    vi.spyOn(ticketsApi, 'getTicketByIdApi').mockResolvedValue(ticketOne);
    const getAuditSpy = vi
      .spyOn(auditApi, 'getTicketAuditLogsApi')
      .mockResolvedValue([]);

    const secondAttachment: AttachmentResponse = {
      ...sampleAttachment,
      id: 502,
      originalFileName: 'receipt.png',
      contentType: 'image/png',
    };

    vi.spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      // Initial pre-fetch (1 item)
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      })
      // Post-upload refresh (2 items)
      .mockResolvedValueOnce({
        content: [sampleAttachment, secondAttachment],
        page: 0,
        size: 10,
        totalElements: 2,
        totalPages: 1,
        first: true,
        last: true,
      })
      // Post-delete refresh (1 item remaining)
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      });

    vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi').mockResolvedValue(secondAttachment);
    vi.spyOn(attachmentsApi, 'deleteTicketAttachmentApi').mockResolvedValue(undefined);

    renderPage('/tickets/1');

    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => expect(attachmentsTab).toHaveTextContent('1'));
    fireEvent.click(attachmentsTab);

    // Upload file
    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    const pngFile = new File(['png'], 'receipt.png', { type: 'image/png' });
    fireEvent.change(fileInput, { target: { files: [pngFile] } });
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    await waitFor(() => {
      expect(screen.getByText('receipt.png')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toHaveTextContent('2');
      expect(getAuditSpy).toHaveBeenCalledTimes(2);
    });

    // Delete newly uploaded attachment
    fireEvent.click(screen.getByRole('button', { name: 'Delete receipt.png' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));

    await waitFor(() => {
      expect(screen.queryByText('receipt.png')).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toHaveTextContent('1');
      expect(getAuditSpy).toHaveBeenCalledTimes(3);
    });
  });

  it('silently refreshes ticket on 409 conflict without losing active tab and hides controls when CLOSED', async () => {
    const getTicketSpy = vi
      .spyOn(ticketsApi, 'getTicketByIdApi')
      .mockResolvedValueOnce(ticketOne)
      .mockResolvedValueOnce({
        ...ticketOne,
        status: 'CLOSED',
      });

    const getAuditSpy = vi
      .spyOn(auditApi, 'getTicketAuditLogsApi')
      .mockResolvedValue([]);

    vi.spyOn(attachmentsApi, 'getTicketAttachmentsApi').mockResolvedValue({
      content: [sampleAttachment],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi').mockRejectedValueOnce({
      status: 409,
      message: 'Cannot upload attachments to a closed ticket',
    });

    renderPage('/tickets/1');

    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => expect(attachmentsTab).toHaveTextContent('1'));
    fireEvent.click(attachmentsTab);

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    fireEvent.change(fileInput, {
      target: { files: [new File(['%PDF-1.4'], 'doc.pdf', { type: 'application/pdf' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    await waitFor(() => {
      // Silent refresh fetched CLOSED ticket & audit logs
      expect(getTicketSpy).toHaveBeenCalledTimes(2);
      expect(getAuditSpy).toHaveBeenCalledTimes(2);
      // Active Attachments tab preserved and read-only closed UI applied
      expect(
        screen.getByText(/This ticket is closed\. Existing attachments can still be viewed and downloaded/i)
      ).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Select File/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Delete/i })).not.toBeInTheDocument();
      expect(screen.getByText('invoice-april.pdf')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Download invoice-april.pdf' })).toBeEnabled();
    });
  });

  it('jumps to the last page after uploading from an earlier page crosses a page boundary', async () => {
    vi.spyOn(ticketsApi, 'getTicketByIdApi').mockResolvedValue(ticketOne);
    const getAuditSpy = vi.spyOn(auditApi, 'getTicketAuditLogsApi').mockResolvedValue([]);

    const eleventhAttachment: AttachmentResponse = {
      ...sampleAttachment,
      id: 511,
      originalFileName: 'page-2-upload.pdf',
    };

    const getAttachmentsSpy = vi
      .spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      // Initial pre-fetch on page 0 (10 items -> 1 page)
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 10,
        totalPages: 1,
        first: true,
        last: true,
      })
      // Post-upload refresh starting on current page 0 reveals totalPages: 2
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 11,
        totalPages: 2,
        first: true,
        last: false,
      })
      // Automatic jumpToLastPage fetch for page 1
      .mockResolvedValueOnce({
        content: [eleventhAttachment],
        page: 1,
        size: 10,
        totalElements: 11,
        totalPages: 2,
        first: false,
        last: true,
      });

    vi.spyOn(attachmentsApi, 'uploadTicketAttachmentApi').mockResolvedValue(eleventhAttachment);

    renderPage('/tickets/1');

    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => expect(attachmentsTab).toHaveTextContent('10'));
    fireEvent.click(attachmentsTab);

    const fileInput = document.getElementById('attachment-file-input') as HTMLInputElement;
    fireEvent.change(fileInput, {
      target: { files: [new File(['%PDF-1.4'], 'page-2-upload.pdf', { type: 'application/pdf' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: /^Upload$/i }));

    await waitFor(() => {
      expect(getAttachmentsSpy).toHaveBeenNthCalledWith(1, 1, { page: 0, size: 10 });
      expect(getAttachmentsSpy).toHaveBeenNthCalledWith(2, 1, { page: 0, size: 10 });
      expect(getAttachmentsSpy).toHaveBeenNthCalledWith(3, 1, { page: 1, size: 10 });
      expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
      expect(screen.getByText('page-2-upload.pdf')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toHaveTextContent('11');
      expect(getAuditSpy).toHaveBeenCalledTimes(2);
    });
  });

  it('clamps to the previous valid page when deleting the last item from a non-zero page', async () => {
    vi.spyOn(ticketsApi, 'getTicketByIdApi').mockResolvedValue(ticketOne);
    const getAuditSpy = vi.spyOn(auditApi, 'getTicketAuditLogsApi').mockResolvedValue([]);

    const lastPageAttachment: AttachmentResponse = {
      ...sampleAttachment,
      id: 601,
      originalFileName: 'last-item-page-2.png',
      contentType: 'image/png',
    };

    const getAttachmentsSpy = vi
      .spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      // Initial pre-fetch (page 0 of 2)
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 11,
        totalPages: 2,
        first: true,
        last: false,
      })
      // User clicks Next page -> page 1 with 1 item
      .mockResolvedValueOnce({
        content: [lastPageAttachment],
        page: 1,
        size: 10,
        totalElements: 11,
        totalPages: 2,
        first: false,
        last: true,
      })
      // Post-delete refresh on page 1 returns empty content with totalPages: 1
      .mockResolvedValueOnce({
        content: [],
        page: 1,
        size: 10,
        totalElements: 10,
        totalPages: 1,
        first: false,
        last: true,
      })
      // Clamped fallback fetch on page 0
      .mockResolvedValueOnce({
        content: [sampleAttachment],
        page: 0,
        size: 10,
        totalElements: 10,
        totalPages: 1,
        first: true,
        last: true,
      });

    vi.spyOn(attachmentsApi, 'deleteTicketAttachmentApi').mockResolvedValue(undefined);

    renderPage('/tickets/1');

    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => expect(attachmentsTab).toHaveTextContent('11'));
    fireEvent.click(attachmentsTab);

    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() => {
      expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
      expect(screen.getByText('last-item-page-2.png')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Delete last-item-page-2.png' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Attachment' }));

    await waitFor(() => {
      expect(getAttachmentsSpy).toHaveBeenNthCalledWith(3, 1, { page: 1, size: 10 });
      expect(getAttachmentsSpy).toHaveBeenNthCalledWith(4, 1, { page: 0, size: 10 });
      expect(screen.getByText('invoice-april.pdf')).toBeInTheDocument();
      expect(screen.queryByText('last-item-page-2.png')).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toHaveTextContent('10');
      expect(getAuditSpy).toHaveBeenCalledTimes(2);
    });
  });

  it('recovers via Retry when initial attachment pre-fetch fails', async () => {
    vi.spyOn(ticketsApi, 'getTicketByIdApi').mockResolvedValue(ticketOne);

    const getAttachmentsSpy = vi
      .spyOn(attachmentsApi, 'getTicketAttachmentsApi')
      .mockRejectedValueOnce({ status: 500, message: 'Attachment service unavailable' })
      .mockResolvedValueOnce(pageOneResponse);

    renderPage('/tickets/1');

    const attachmentsTab = await screen.findByRole('button', { name: /Attachments/i });
    await waitFor(() => {
      expect(getAttachmentsSpy).toHaveBeenCalledTimes(1);
      expect(attachmentsTab).toHaveTextContent('0');
    });

    fireEvent.click(attachmentsTab);
    expect(screen.getByText('Attachment service unavailable')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Retry/i }));

    await waitFor(() => {
      expect(getAttachmentsSpy).toHaveBeenCalledTimes(2);
      expect(getAttachmentsSpy).toHaveBeenNthCalledWith(2, 1, { page: 0, size: 10 });
      expect(screen.getByText('invoice-april.pdf')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toHaveTextContent('3');
    });
  });
});

