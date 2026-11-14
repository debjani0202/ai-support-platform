import { describe, expect, it, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CreateTicketPage } from '@/pages/tickets/CreateTicketPage';
import * as aiApi from '@/lib/api/ai';
import * as ticketsApi from '@/lib/api/tickets';
import * as useAuthHook from '@/hooks/useAuth';
import type { TicketPriority } from '@/types/ticket.types';
import { toast } from 'sonner';

vi.mock('@/lib/api/ai');
vi.mock('@/lib/api/tickets');
vi.mock('@/hooks/useAuth');
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('CreateTicketPage - AI Priority Suggestion (CP2 Stage B)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useAuthHook, 'useAuth').mockReturnValue({
      user: {
        userId: 10,
        name: 'Jane Customer',
        email: 'jane@example.com',
        role: 'CUSTOMER',
      },
      refreshSession: vi.fn(),
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

  const renderComponent = () => {
    return render(
      <MemoryRouter>
        <CreateTicketPage />
      </MemoryRouter>
    );
  };

  it('1. renders "Suggest priority" button alongside Priority Level control', () => {
    renderComponent();

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    expect(suggestBtn).toBeInTheDocument();
    expect(screen.getByLabelText(/Priority Level/i)).toBeInTheDocument();
  });

  it('2. button is disabled when title or description is missing or invalid', () => {
    renderComponent();

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    // Initially empty, button must be disabled
    expect(suggestBtn).toBeDisabled();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    // Fill only title with whitespace
    fireEvent.change(titleInput, { target: { value: '   ' } });
    expect(suggestBtn).toBeDisabled();

    // Fill title with valid text, description still empty
    fireEvent.change(titleInput, { target: { value: 'Checkout broken' } });
    expect(suggestBtn).toBeDisabled();

    // Fill description with whitespace only
    fireEvent.change(descInput, { target: { value: '   ' } });
    expect(suggestBtn).toBeDisabled();

    // Fill both with valid content -> button becomes enabled
    fireEvent.change(descInput, { target: { value: 'Users cannot complete purchase' } });
    expect(suggestBtn).not.toBeDisabled();

    // Title exceeding 255 characters -> button becomes disabled
    fireEvent.change(titleInput, { target: { value: 'A'.repeat(256) } });
    expect(suggestBtn).toBeDisabled();
  });

  it('3. displays loading state ("Analysing ticket...") while request is in-flight', async () => {
    let resolvePromise: (value: { priority: TicketPriority }) => void = () => {};
    const pendingPromise = new Promise<{ priority: TicketPriority }>((resolve) => {
      resolvePromise = resolve;
    });

    vi.spyOn(aiApi, 'suggestPriorityApi').mockReturnValue(pendingPromise);
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Outage alert' } });
    fireEvent.change(descInput, { target: { value: 'System is completely down' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    // Button should now be disabled and display loading text
    expect(suggestBtn).toBeDisabled();
    expect(screen.getByText(/Analysing ticket\.\.\./i)).toBeInTheDocument();

    // Complete the call
    resolvePromise({ priority: 'URGENT' });

    await waitFor(() => {
      expect(screen.getByText(/AI SUGGESTION/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /suggest priority/i })).not.toBeDisabled();
    });
  });

  it('4. successful priority suggestion displays suggested priority badge and apply action', async () => {
    vi.spyOn(aiApi, 'suggestPriorityApi').mockResolvedValue({ priority: 'HIGH' });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Payment gateway failing' } });
    fireEvent.change(descInput, { target: { value: 'Stripe webhook 500 errors' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText(/AI SUGGESTION/i)).toBeInTheDocument();
      expect(screen.getByText(/Suggested priority:/i)).toBeInTheDocument();
      expect(screen.getByText('High')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /apply suggestion/i })).toBeInTheDocument();
    });
  });

  it('5. applying suggestion updates the existing priority selection and shows confirmation toast', async () => {
    vi.spyOn(aiApi, 'suggestPriorityApi').mockResolvedValue({ priority: 'URGENT' });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);
    const prioritySelect = screen.getByLabelText(/Priority Level/i) as HTMLSelectElement;

    // Default priority is MEDIUM
    expect(prioritySelect.value).toBe('MEDIUM');

    fireEvent.change(titleInput, { target: { value: 'Production down' } });
    fireEvent.change(descInput, { target: { value: 'All customer access blocked' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText(/AI SUGGESTION/i)).toBeInTheDocument();
    });

    // Dropdown still unchanged before user clicks apply
    expect(prioritySelect.value).toBe('MEDIUM');

    // Click Apply suggestion
    const applyBtn = screen.getByRole('button', { name: /apply suggestion/i });
    fireEvent.click(applyBtn);

    expect(prioritySelect.value).toBe('URGENT');
    expect(toast.info).toHaveBeenCalledWith('Priority updated to Urgent');
  });

  it('6. suggestion does not automatically overwrite manual selection', async () => {
    vi.spyOn(aiApi, 'suggestPriorityApi').mockResolvedValue({ priority: 'LOW' });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);
    const prioritySelect = screen.getByLabelText(/Priority Level/i) as HTMLSelectElement;

    // User explicitly selects HIGH
    fireEvent.change(prioritySelect, { target: { value: 'HIGH' } });
    expect(prioritySelect.value).toBe('HIGH');

    fireEvent.change(titleInput, { target: { value: 'Small UI typo' } });
    fireEvent.change(descInput, { target: { value: 'Typo in footer link' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText('Low')).toBeInTheDocument();
    });

    // Manual selection remains HIGH until user explicitly clicks apply
    expect(prioritySelect.value).toBe('HIGH');
  });

  it('7. null priority response displays friendly notice and does not show suggestion badge', async () => {
    vi.spyOn(aiApi, 'suggestPriorityApi').mockResolvedValue({ priority: null });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Unclear query' } });
    fireEvent.change(descInput, { target: { value: 'Need some general information' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/We couldn't determine a reliable priority\. Please choose one manually\./i)
      ).toBeInTheDocument();
    });

    expect(screen.queryByText(/AI SUGGESTION/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();
  });

  it('8. handles API error gracefully and allows user to retry without blocking form', async () => {
    const suggestSpy = vi
      .spyOn(aiApi, 'suggestPriorityApi')
      .mockRejectedValueOnce(new Error('Connection timed out'))
      .mockResolvedValueOnce({ priority: 'MEDIUM' });

    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Search bug' } });
    fireEvent.change(descInput, { target: { value: 'Search filter needs two clicks' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText(/Connection timed out/i)).toBeInTheDocument();
    });

    // Form can still be submitted or retry can be performed
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.queryByText(/Connection timed out/i)).not.toBeInTheDocument();
      expect(screen.getByText('Medium')).toBeInTheDocument();
    });

    expect(suggestSpy).toHaveBeenCalledTimes(2);
  });

  it('9. prevents duplicate in-flight requests while request is already running', async () => {
    let resolvePromise: (value: { priority: TicketPriority }) => void = () => {};
    const pendingPromise = new Promise<{ priority: TicketPriority }>((resolve) => {
      resolvePromise = resolve;
    });

    const suggestSpy = vi.spyOn(aiApi, 'suggestPriorityApi').mockReturnValue(pendingPromise);
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Issue title' } });
    fireEvent.change(descInput, { target: { value: 'Issue description' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    expect(suggestSpy).toHaveBeenCalledTimes(1);
    expect(suggestBtn).toBeDisabled();

    // Second click during in-flight request
    fireEvent.click(suggestBtn);
    expect(suggestSpy).toHaveBeenCalledTimes(1);

    resolvePromise({ priority: 'HIGH' });

    await waitFor(() => {
      expect(screen.getByText('High')).toBeInTheDocument();
      expect(suggestBtn).not.toBeDisabled();
    });
  });

  it('10. stale response handling: editing title or description invalidates in-flight response', async () => {
    let resolveSlowRequest: (value: { priority: TicketPriority }) => void = () => {};
    const slowPromise = new Promise<{ priority: TicketPriority }>((resolve) => {
      resolveSlowRequest = resolve;
    });

    vi.spyOn(aiApi, 'suggestPriorityApi').mockReturnValue(slowPromise);
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Initial title' } });
    fireEvent.change(descInput, { target: { value: 'Initial description' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    // User modifies title while request is in flight
    fireEvent.change(titleInput, { target: { value: 'Changed title after click' } });

    // Now resolve slow request
    resolveSlowRequest({ priority: 'URGENT' });

    // Stale suggestion should NOT be displayed
    await waitFor(() => {
      expect(screen.queryByText(/AI SUGGESTION/i)).not.toBeInTheDocument();
      expect(screen.queryByText('Urgent')).not.toBeInTheDocument();
    });
  });

  it('11. invalidated suggestion is removed when title or description changes', async () => {
    vi.spyOn(aiApi, 'suggestPriorityApi').mockResolvedValue({ priority: 'HIGH' });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Major bug' } });
    fireEvent.change(descInput, { target: { value: 'System impaired' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest priority/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText(/AI SUGGESTION/i)).toBeInTheDocument();
      expect(screen.getByText('High')).toBeInTheDocument();
    });

    // Editing description clears the suggestion
    fireEvent.change(descInput, { target: { value: 'Different description completely' } });

    expect(screen.queryByText(/AI SUGGESTION/i)).not.toBeInTheDocument();
    expect(screen.queryByText('High')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();
  });

  it('12. existing ticket creation functionality remains intact', async () => {
    const createTicketSpy = vi.spyOn(ticketsApi, 'createTicketApi').mockResolvedValue({
      id: 99,
      title: 'Crash on login',
      description: 'App crashes immediately on login',
      status: 'OPEN',
      category: 'BUG_REPORT',
      priority: 'HIGH',
      createdBy: 10,
      assignedTo: null,
      createdAt: '2026-11-14T20:00:00Z',
      updatedAt: '2026-11-14T20:00:00Z',
    });

    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);
    const prioritySelect = screen.getByLabelText(/Priority Level/i);

    fireEvent.change(titleInput, { target: { value: 'Crash on login' } });
    fireEvent.change(descInput, { target: { value: 'App crashes immediately on login' } });
    fireEvent.change(prioritySelect, { target: { value: 'HIGH' } });

    const submitBtn = screen.getByRole('button', { name: /create ticket/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createTicketSpy).toHaveBeenCalledWith({
        title: 'Crash on login',
        description: 'App crashes immediately on login',
        priority: 'HIGH',
        category: null,
      });
      expect(toast.success).toHaveBeenCalledWith(
        'Ticket #99 created successfully! Categorized as BUG_REPORT.'
      );
      expect(mockNavigate).toHaveBeenCalledWith('/tickets/99');
    });
  });
});
