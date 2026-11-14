import { describe, expect, it, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CreateTicketPage } from '@/pages/tickets/CreateTicketPage';
import * as aiApi from '@/lib/api/ai';
import * as ticketsApi from '@/lib/api/tickets';
import * as useAuthHook from '@/hooks/useAuth';
import type { TicketCategory } from '@/types/ticket.types';
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

describe('CreateTicketPage - AI Classification Preview (CP1 Stage B)', () => {
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

  it('renders "Suggest Category" button and initial AI auto-classification banner', () => {
    renderComponent();

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    expect(suggestBtn).toBeInTheDocument();

    expect(screen.getByText(/Category \(Optional\)/i)).toBeInTheDocument();
    expect(screen.getByText(/AI Auto-Classification Enabled:/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Category \(Optional\)/i)).toBeInTheDocument();
  });

  it('shows inline validation error and does not call API when title or description is missing', async () => {
    const classifySpy = vi.spyOn(aiApi, 'classifyTicketApi');
    renderComponent();

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Please provide both a title and description before requesting a category suggestion\./i)
      ).toBeInTheDocument();
    });

    expect(classifySpy).not.toHaveBeenCalled();

    // Fill only title
    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    fireEvent.change(titleInput, { target: { value: 'Payment failed' } });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Please provide both a title and description before requesting a category suggestion\./i)
      ).toBeInTheDocument();
    });
    expect(classifySpy).not.toHaveBeenCalled();
  });

  it('prevents duplicate in-flight classification requests while request is loading', async () => {
    let resolvePromise: (value: { category: 'PAYMENT' }) => void = () => {};
    const pendingPromise = new Promise<{ category: 'PAYMENT' }>((resolve) => {
      resolvePromise = resolve;
    });

    const classifySpy = vi.spyOn(aiApi, 'classifyTicketApi').mockReturnValue(pendingPromise);
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Invoice query' } });
    fireEvent.change(descInput, { target: { value: 'Double charged on my card' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    // Button should be disabled / loading
    expect(suggestBtn).toBeDisabled();
    expect(classifySpy).toHaveBeenCalledTimes(1);

    // Attempt second click while first is pending
    fireEvent.click(suggestBtn);
    expect(classifySpy).toHaveBeenCalledTimes(1);

    // Resolve API call
    resolvePromise({ category: 'PAYMENT' });

    await waitFor(() => {
      expect(screen.getByText(/Suggested:/i)).toBeInTheDocument();
      expect(screen.getByText('Payment')).toBeInTheDocument();
      expect(suggestBtn).not.toBeDisabled();
    });
  });

  it('displays suggested category and applies it to form dropdown when user clicks "Apply Suggestion"', async () => {
    vi.spyOn(aiApi, 'classifyTicketApi').mockResolvedValue({ category: 'ACCOUNT_ACCESS' });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Cannot log in' } });
    fireEvent.change(descInput, { target: { value: 'Password reset link expired' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText(/Suggested:/i)).toBeInTheDocument();
      expect(screen.getByText(/Suggested:/i).parentElement).toHaveTextContent('Account Access');
    });

    const applyBtn = screen.getByRole('button', { name: /apply suggestion/i });
    expect(applyBtn).toBeInTheDocument();

    // Category select should still be default auto-classify before clicking apply
    const categorySelect = screen.getByLabelText(/Category \(Optional\)/i) as HTMLSelectElement;
    expect(categorySelect.value).toBe('');

    // Click Apply
    fireEvent.click(applyBtn);

    expect(categorySelect.value).toBe('ACCOUNT_ACCESS');
    expect(toast.info).toHaveBeenCalledWith('Applied suggested category: ACCOUNT ACCESS');
  });

  it('clears previous AI suggestion whenever title or description changes', async () => {
    vi.spyOn(aiApi, 'classifyTicketApi').mockResolvedValue({ category: 'BUG_REPORT' });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Crash on clicking save' } });
    fireEvent.change(descInput, { target: { value: 'NullPointerException shown on screen' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText(/Suggested:/i)).toBeInTheDocument();
      expect(screen.getByText(/Suggested:/i).parentElement).toHaveTextContent('Bug Report');
    });

    // Change title -> suggestion callout should vanish
    fireEvent.change(titleInput, { target: { value: 'Crash on clicking save (updated)' } });

    await waitFor(() => {
      expect(screen.queryByText(/Suggested:/i)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();
    });
  });

  it('shows informational notice when AI classification returns null category', async () => {
    vi.spyOn(aiApi, 'classifyTicketApi').mockResolvedValue({ category: null });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Hello' } });
    fireEvent.change(descInput, { target: { value: 'Just saying hi' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/AI could not determine a specific category\. You may choose one manually or leave auto-classify enabled\./i)
      ).toBeInTheDocument();
    });

    expect(screen.queryByText(/Suggested:/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();
  });

  it('displays user-friendly error message when classification API fails', async () => {
    vi.spyOn(aiApi, 'classifyTicketApi').mockRejectedValue({
      message: 'Failed to connect to classification service',
    });
    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Database failure' } });
    fireEvent.change(descInput, { target: { value: 'Connection timeout error' } });

    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByText('Failed to connect to classification service')).toBeInTheDocument();
    });

    expect(screen.queryByText(/Suggested:/i)).not.toBeInTheDocument();
  });

  it('submitting ticket with category null preserves automatic backend classification payload', async () => {
    const createSpy = vi.spyOn(ticketsApi, 'createTicketApi').mockResolvedValue({
      id: 99,
      title: 'Auto ticket',
      description: 'Auto ticket description',
      status: 'OPEN',
      priority: 'MEDIUM',
      category: 'TECHNICAL_ISSUE',
      createdBy: 10,
      assignedTo: null,
      createdAt: '2026-11-13T10:00:00Z',
      updatedAt: '2026-11-13T10:00:00Z',
    });

    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'System latency issue' } });
    fireEvent.change(descInput, { target: { value: 'Response times are exceeding 5 seconds' } });

    const submitBtn = screen.getByRole('button', { name: /create ticket/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        title: 'System latency issue',
        description: 'Response times are exceeding 5 seconds',
        priority: 'MEDIUM',
        category: null,
      });
      expect(mockNavigate).toHaveBeenCalledWith('/tickets/99');
    });
  });

  it('submitting ticket after applying AI suggested category sends explicit category payload', async () => {
    vi.spyOn(aiApi, 'classifyTicketApi').mockResolvedValue({ category: 'PAYMENT' });
    const createSpy = vi.spyOn(ticketsApi, 'createTicketApi').mockResolvedValue({
      id: 100,
      title: 'Refund requested',
      description: 'Double billed on invoice #442',
      status: 'OPEN',
      priority: 'HIGH',
      category: 'PAYMENT',
      createdBy: 10,
      assignedTo: null,
      createdAt: '2026-11-13T10:00:00Z',
      updatedAt: '2026-11-13T10:00:00Z',
    });

    renderComponent();

    const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
    const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

    fireEvent.change(titleInput, { target: { value: 'Refund requested' } });
    fireEvent.change(descInput, { target: { value: 'Double billed on invoice #442' } });

    // Suggest category
    const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
    fireEvent.click(suggestBtn);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /apply suggestion/i })).toBeInTheDocument();
    });

    // Apply suggestion
    fireEvent.click(screen.getByRole('button', { name: /apply suggestion/i }));

    // Change priority
    const prioritySelect = screen.getByLabelText(/Priority Level/i);
    fireEvent.change(prioritySelect, { target: { value: 'HIGH' } });

    // Submit
    const submitBtn = screen.getByRole('button', { name: /create ticket/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        title: 'Refund requested',
        description: 'Double billed on invoice #442',
        priority: 'HIGH',
        category: 'PAYMENT',
      });
      expect(mockNavigate).toHaveBeenCalledWith('/tickets/100');
    });
  });

  describe('Race Condition & Request Invalidation Safeguards', () => {
    function createDeferred<T>() {
      let resolve!: (val: T) => void;
      let reject!: (err: unknown) => void;
      const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
      });
      return { promise, resolve, reject };
    }

    it('changing the title while classification is pending discards the old response', async () => {
      const deferred = createDeferred<{ category: TicketCategory | null }>();
      vi.spyOn(aiApi, 'classifyTicketApi').mockReturnValue(deferred.promise);
      renderComponent();

      const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
      const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

      fireEvent.change(titleInput, { target: { value: 'Old Title' } });
      fireEvent.change(descInput, { target: { value: 'Detailed description of issue' } });

      const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
      fireEvent.click(suggestBtn);

      expect(suggestBtn).toBeDisabled();

      // User edits the title while request is in flight
      fireEvent.change(titleInput, { target: { value: 'New Different Title' } });

      // Request is invalidated and loading resets
      expect(suggestBtn).not.toBeDisabled();

      // Resolve the old in-flight request
      deferred.resolve({ category: 'PAYMENT' });

      // Wait and verify suggestion is NOT displayed
      await waitFor(() => {
        expect(screen.queryByText(/Suggested:/i)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();
      });
    });

    it('changing the description while classification is pending discards the old response', async () => {
      const deferred = createDeferred<{ category: TicketCategory | null }>();
      vi.spyOn(aiApi, 'classifyTicketApi').mockReturnValue(deferred.promise);
      renderComponent();

      const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
      const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

      fireEvent.change(titleInput, { target: { value: 'Some Title' } });
      fireEvent.change(descInput, { target: { value: 'Old Description text' } });

      const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
      fireEvent.click(suggestBtn);

      expect(suggestBtn).toBeDisabled();

      // User edits description while request is in flight
      fireEvent.change(descInput, { target: { value: 'Completely new description text' } });

      expect(suggestBtn).not.toBeDisabled();

      // Resolve old response
      deferred.resolve({ category: 'TECHNICAL_ISSUE' });

      await waitFor(() => {
        expect(screen.queryByText(/Suggested:/i)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();
      });
    });

    it('a stale null response does not display an outdated notice', async () => {
      const deferred = createDeferred<{ category: TicketCategory | null }>();
      vi.spyOn(aiApi, 'classifyTicketApi').mockReturnValue(deferred.promise);
      renderComponent();

      const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
      const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

      fireEvent.change(titleInput, { target: { value: 'Vague query' } });
      fireEvent.change(descInput, { target: { value: 'Short note' } });

      const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
      fireEvent.click(suggestBtn);

      // Edit title while in flight
      fireEvent.change(titleInput, { target: { value: 'Vague query updated' } });

      // Resolve old response with null
      deferred.resolve({ category: null });

      await waitFor(() => {
        expect(
          screen.queryByText(/AI could not determine a specific category/i)
        ).not.toBeInTheDocument();
      });
    });

    it('a stale error does not overwrite the current UI', async () => {
      const deferred = createDeferred<{ category: TicketCategory | null }>();
      vi.spyOn(aiApi, 'classifyTicketApi').mockReturnValue(deferred.promise);
      renderComponent();

      const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
      const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

      fireEvent.change(titleInput, { target: { value: 'Server error check' } });
      fireEvent.change(descInput, { target: { value: 'Crash details here' } });

      const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
      fireEvent.click(suggestBtn);

      // Edit title while in flight
      fireEvent.change(titleInput, { target: { value: 'Server error check updated' } });

      // Reject old response with error
      deferred.reject({ message: 'Ollama model timeout' });

      await waitFor(() => {
        expect(screen.queryByText('Ollama model timeout')).not.toBeInTheDocument();
      });
    });

    it('rapid repeated clicks do not produce duplicate API calls', async () => {
      const deferred = createDeferred<{ category: TicketCategory | null }>();
      const classifySpy = vi.spyOn(aiApi, 'classifyTicketApi').mockReturnValue(deferred.promise);
      renderComponent();

      const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
      const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

      fireEvent.change(titleInput, { target: { value: 'Rapid clicks title' } });
      fireEvent.change(descInput, { target: { value: 'Rapid clicks description' } });

      const suggestBtn = screen.getByRole('button', { name: /suggest category/i });

      // Trigger multiple rapid clicks synchronously
      fireEvent.click(suggestBtn);
      fireEvent.click(suggestBtn);
      fireEvent.click(suggestBtn);

      // Must be called exactly once
      expect(classifySpy).toHaveBeenCalledTimes(1);

      deferred.resolve({ category: 'ACCOUNT_ACCESS' });
      await waitFor(() => {
        expect(screen.getByText(/Suggested:/i)).toBeInTheDocument();
      });
    });

    it('loading state is correctly handled when a request becomes stale and allows subsequent requests', async () => {
      const deferred1 = createDeferred<{ category: TicketCategory | null }>();
      const deferred2 = createDeferred<{ category: TicketCategory | null }>();

      const classifySpy = vi
        .spyOn(aiApi, 'classifyTicketApi')
        .mockReturnValueOnce(deferred1.promise)
        .mockReturnValueOnce(deferred2.promise);

      renderComponent();

      const titleInput = screen.getByPlaceholderText(/Cannot process credit card/i);
      const descInput = screen.getByPlaceholderText(/Provide complete steps to reproduce/i);

      fireEvent.change(titleInput, { target: { value: 'First issue' } });
      fireEvent.change(descInput, { target: { value: 'First description' } });

      const suggestBtn = screen.getByRole('button', { name: /suggest category/i });
      fireEvent.click(suggestBtn);

      expect(suggestBtn).toBeDisabled();
      expect(classifySpy).toHaveBeenCalledTimes(1);

      // User updates title -> request 1 becomes stale, loading resets
      fireEvent.change(titleInput, { target: { value: 'Second issue' } });
      expect(suggestBtn).not.toBeDisabled();

      // User triggers request 2 for new title
      fireEvent.click(suggestBtn);
      expect(suggestBtn).toBeDisabled();
      expect(classifySpy).toHaveBeenCalledTimes(2);

      // Old request 1 resolves with PAYMENT
      deferred1.resolve({ category: 'PAYMENT' });

      // Ensure request 1's finally did NOT prematurely clear request 2's loading state
      expect(suggestBtn).toBeDisabled();
      expect(screen.queryByText(/Suggested:/i)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /apply suggestion/i })).not.toBeInTheDocument();

      // Now request 2 resolves with BUG_REPORT
      deferred2.resolve({ category: 'BUG_REPORT' });

      await waitFor(() => {
        expect(suggestBtn).not.toBeDisabled();
        expect(screen.getByText(/Suggested:/i)).toBeInTheDocument();
        expect(screen.getByText(/Suggested:/i).parentElement).toHaveTextContent('Bug Report');
      });
    });
  });
});
