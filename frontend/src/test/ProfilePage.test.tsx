import { describe, expect, it, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ProfilePage } from '@/pages/profile/ProfilePage';
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

describe('ProfilePage', () => {
  const mockRefreshSession = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user: {
        userId: 10,
        name: 'Jane Doe',
        email: 'jane@example.com',
        role: 'CUSTOMER',
      },
      refreshSession: mockRefreshSession,
      isAuthenticated: true,
      isInitializing: false,
      login: vi.fn(),
      logout: vi.fn(),
      token: 'jwt-token',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders account details correctly for customer', async () => {
    vi.spyOn(agentRequestsApi, 'getMyAgentRequestApi').mockResolvedValue(null);

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    expect(screen.getByText('User Profile')).toBeDefined();
    expect(screen.getAllByText('Jane Doe').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('jane@example.com').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('CUSTOMER').length).toBeGreaterThanOrEqual(1);

    await waitFor(() => {
      expect(screen.getByText('Become a Support Agent')).toBeDefined();
    });
  });

  it('allows customer to submit an agent access request', async () => {
    vi.spyOn(agentRequestsApi, 'getMyAgentRequestApi').mockResolvedValue(null);
    vi.spyOn(agentRequestsApi, 'submitAgentRequestApi').mockResolvedValue({
      id: 1,
      userId: 10,
      userName: 'Jane Doe',
      userEmail: 'jane@example.com',
      status: 'PENDING',
      reason: 'I have 3 years of support experience',
      adminNotes: null,
      rejectionReason: null,
      reviewedBy: null,
      reviewerName: null,
      reviewedAt: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Apply Now')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Apply Now'));

    const textarea = screen.getByLabelText(/Why do you want to become a Support Agent/i);
    fireEvent.change(textarea, { target: { value: 'I have 3 years of support experience' } });

    fireEvent.click(screen.getByText('Submit Agent Request'));

    await waitFor(() => {
      expect(agentRequestsApi.submitAgentRequestApi).toHaveBeenCalledWith({
        reason: 'I have 3 years of support experience',
      });
      expect(screen.getByText('Request Pending Review')).toBeDefined();
    });
  });

  it('displays PENDING status when request is awaiting admin review', async () => {
    vi.spyOn(agentRequestsApi, 'getMyAgentRequestApi').mockResolvedValue({
      id: 2,
      userId: 10,
      userName: 'Jane Doe',
      userEmail: 'jane@example.com',
      status: 'PENDING',
      reason: 'Motivated to assist team',
      adminNotes: null,
      rejectionReason: null,
      reviewedBy: null,
      reviewerName: null,
      reviewedAt: null,
      createdAt: '2026-11-01T10:00:00Z',
      updatedAt: '2026-11-01T10:00:00Z',
    });

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Request Pending Review')).toBeDefined();
      expect(screen.getByText('“Motivated to assist team”')).toBeDefined();
      expect(screen.getByText('PENDING')).toBeDefined();
    });
  });

  it('displays APPROVED status with workspace activation button', async () => {
    vi.spyOn(agentRequestsApi, 'getMyAgentRequestApi').mockResolvedValue({
      id: 3,
      userId: 10,
      userName: 'Jane Doe',
      userEmail: 'jane@example.com',
      status: 'APPROVED',
      reason: 'Support skills',
      adminNotes: 'Welcome to the agent team!',
      rejectionReason: null,
      reviewedBy: 1,
      reviewerName: 'Admin Chief',
      reviewedAt: '2026-11-02T12:00:00Z',
      createdAt: '2026-11-01T10:00:00Z',
      updatedAt: '2026-11-02T12:00:00Z',
    });

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Request Approved!')).toBeDefined();
      expect(screen.getByText(/Welcome to the agent team!/i)).toBeDefined();
      expect(screen.getByText('Activate Agent Workspace')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Activate Agent Workspace'));
    expect(mockRefreshSession).toHaveBeenCalledTimes(1);
  });

  it('displays REJECTED status with reason and allows reapplication', async () => {
    vi.spyOn(agentRequestsApi, 'getMyAgentRequestApi').mockResolvedValue({
      id: 4,
      userId: 10,
      userName: 'Jane Doe',
      userEmail: 'jane@example.com',
      status: 'REJECTED',
      reason: 'My first try',
      adminNotes: null,
      rejectionReason: 'More customer service experience required before promotion.',
      reviewedBy: 1,
      reviewerName: 'Admin Chief',
      reviewedAt: '2026-11-02T12:00:00Z',
      createdAt: '2026-11-01T10:00:00Z',
      updatedAt: '2026-11-02T12:00:00Z',
    });

    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Request Declined')).toBeDefined();
      expect(screen.getByText('More customer service experience required before promotion.')).toBeDefined();
      expect(screen.getByText('Apply Again')).toBeDefined();
    });

    fireEvent.click(screen.getByText('Apply Again'));
    expect(screen.getByText('Submit Agent Request')).toBeDefined();
  });
});
