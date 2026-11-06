import { describe, expect, it, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AgentRequestsPage } from '@/pages/admin/AgentRequestsPage';
import * as agentRequestsApi from '@/lib/api/agentRequests';
import * as useAuthHook from '@/hooks/useAuth';

vi.mock('@/lib/api/agentRequests');
vi.mock('@/hooks/useAuth');
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

describe('AgentRequestsPage', () => {
  const mockRequests = [
    {
      id: 101,
      userId: 20,
      userName: 'Alice Walker',
      userEmail: 'alice@example.com',
      status: 'PENDING' as const,
      reason: 'Extensive background in customer success',
      adminNotes: null,
      rejectionReason: null,
      reviewedBy: null,
      reviewerName: null,
      reviewedAt: null,
      createdAt: '2026-11-01T14:30:00Z',
      updatedAt: '2026-11-01T14:30:00Z',
    },
    {
      id: 102,
      userId: 21,
      userName: 'Bob Smith',
      userEmail: 'bob@example.com',
      status: 'APPROVED' as const,
      reason: 'Tech support lead',
      adminNotes: 'Great candidate',
      rejectionReason: null,
      reviewedBy: 1,
      reviewerName: 'Admin Chief',
      reviewedAt: '2026-11-02T10:00:00Z',
      createdAt: '2026-10-31T09:00:00Z',
      updatedAt: '2026-11-02T10:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user: {
        userId: 1,
        name: 'Admin Chief',
        email: 'admin@support.ai',
        role: 'ADMIN',
      },
      refreshSession: vi.fn(),
      isAuthenticated: true,
      isInitializing: false,
      login: vi.fn(),
      logout: vi.fn(),
      token: 'admin-jwt-token',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders application list and filter tabs', async () => {
    vi.spyOn(agentRequestsApi, 'getAdminAgentRequestsApi').mockResolvedValue({
      content: mockRequests,
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      first: true,
      last: true,
    });

    render(
      <MemoryRouter>
        <AgentRequestsPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Agent Access Requests')).toBeDefined();
    expect(screen.getByText('All Requests')).toBeDefined();
    expect(screen.getByText('Pending')).toBeDefined();
    expect(screen.getByText('Approved')).toBeDefined();
    expect(screen.getByText('Rejected')).toBeDefined();

    await waitFor(() => {
      expect(screen.getByText('Alice Walker')).toBeDefined();
      expect(screen.getByText('alice@example.com')).toBeDefined();
      expect(screen.getByText('Bob Smith')).toBeDefined();
      expect(screen.getByText('PENDING')).toBeDefined();
      expect(screen.getByText('APPROVED')).toBeDefined();
    });
  });

  it('allows administrator to approve a pending request', async () => {
    vi.spyOn(agentRequestsApi, 'getAdminAgentRequestsApi').mockResolvedValue({
      content: mockRequests,
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      first: true,
      last: true,
    });
    vi.spyOn(agentRequestsApi, 'approveAgentRequestApi').mockResolvedValue({
      ...mockRequests[0],
      status: 'APPROVED',
      adminNotes: 'Welcome aboard!',
    });

    render(
      <MemoryRouter>
        <AgentRequestsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Alice Walker')).toBeDefined();
    });

    // Click Approve for Alice
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));

    expect(screen.getByText('Approve Agent Application')).toBeDefined();

    const notesTextarea = screen.getByLabelText(/Admin Notes/i);
    fireEvent.change(notesTextarea, { target: { value: 'Welcome aboard!' } });

    fireEvent.click(screen.getByRole('button', { name: 'Confirm Approval' }));

    await waitFor(() => {
      expect(agentRequestsApi.approveAgentRequestApi).toHaveBeenCalledWith(101, {
        adminNotes: 'Welcome aboard!',
      });
    });
  });

  it('enforces mandatory rejection reason when rejecting a request', async () => {
    vi.spyOn(agentRequestsApi, 'getAdminAgentRequestsApi').mockResolvedValue({
      content: mockRequests,
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      first: true,
      last: true,
    });
    vi.spyOn(agentRequestsApi, 'rejectAgentRequestApi').mockResolvedValue({
      ...mockRequests[0],
      status: 'REJECTED',
      rejectionReason: 'Needs additional customer service certification.',
    });

    render(
      <MemoryRouter>
        <AgentRequestsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Alice Walker')).toBeDefined();
    });

    // Click Reject for Alice
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));

    expect(screen.getByText('Reject Agent Application')).toBeDefined();

    // Attempt confirm with empty reason
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Rejection' }));

    expect(
      screen.getByText('Rejection reason must be at least 5 characters long.')
    ).toBeDefined();
    expect(agentRequestsApi.rejectAgentRequestApi).not.toHaveBeenCalled();

    // Fill valid reason
    const reasonTextarea = screen.getByLabelText(/Rejection Reason/i);
    fireEvent.change(reasonTextarea, {
      target: { value: 'Needs additional customer service certification.' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Confirm Rejection' }));

    await waitFor(() => {
      expect(agentRequestsApi.rejectAgentRequestApi).toHaveBeenCalledWith(101, {
        rejectionReason: 'Needs additional customer service certification.',
      });
    });
  });
});
