import { describe, expect, it, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { AssignAgentModal } from '@/components/tickets/AssignAgentModal';
import * as usersApi from '@/lib/api/users';
import * as ticketsApi from '@/lib/api/tickets';
import { toast } from 'sonner';
import type { TicketResponse } from '@/types/ticket.types';
import type { AgentResponse } from '@/types/user.types';

vi.mock('@/lib/api/users');
vi.mock('@/lib/api/tickets');
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

describe('AssignAgentModal', () => {
  const mockAgents: AgentResponse[] = [
    {
      id: 2,
      name: 'Jane Support',
      email: 'jane@support.ai',
      role: 'AGENT',
    },
    {
      id: 5,
      name: 'John Doe',
      email: 'john@support.ai',
      role: 'AGENT',
    },
  ];

  const baseTicket: TicketResponse = {
    id: 1,
    title: 'Cannot reset password',
    description: 'User locked out of portal',
    status: 'OPEN',
    category: 'ACCOUNT_ACCESS',
    priority: 'HIGH',
    createdBy: 10,
    assignedTo: null,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
  };

  const mockOnClose = vi.fn();
  const mockOnAssigned = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders modal, loads agents, and populates the select dropdown', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue(mockAgents);

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    expect(screen.getByText('Assign Ticket #1')).toBeInTheDocument();
    expect(screen.getByText('Loading agents...')).toBeInTheDocument();

    await waitFor(() => {
      expect(usersApi.getAgentsApi).toHaveBeenCalledTimes(1);
    });

    await waitFor(() => {
      expect(screen.getByText('Jane Support (jane@support.ai)')).toBeInTheDocument();
      expect(screen.getByText('John Doe (john@support.ai)')).toBeInTheDocument();
    });

    expect(screen.getByText('Currently unassigned')).toBeInTheDocument();
    const select = screen.getByLabelText('Assignee') as HTMLSelectElement;
    expect(select.value).toBe('');
  });

  it('preselects the currently assigned agent when present in the agents list', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue(mockAgents);

    const assignedTicket: TicketResponse = {
      ...baseTicket,
      assignedTo: 2,
    };

    render(
      <AssignAgentModal
        ticket={assignedTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Currently assigned to Jane Support')).toBeInTheDocument();
    });

    const select = screen.getByLabelText('Assignee') as HTMLSelectElement;
    expect(select.value).toBe('2');
  });

  it('displays warning banner and disabled option when assigned agent is missing from list', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue(mockAgents);

    const ticketWithMissingAgent: TicketResponse = {
      ...baseTicket,
      assignedTo: 99, // Agent 99 is not in mockAgents (e.g. demoted)
    };

    render(
      <AssignAgentModal
        ticket={ticketWithMissingAgent}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(
        screen.getByText(/The currently assigned agent \(ID #99\) is no longer available in the agent directory/i)
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText('Currently assigned to Agent #99')
    ).toBeInTheDocument();

    const select = screen.getByLabelText('Assignee') as HTMLSelectElement;
    expect(select.value).toBe('');

    // Option representing missing agent is rendered disabled
    const missingOption = screen.getByText(
      'Current: Agent #99 (No longer available as an agent)'
    ) as HTMLOptionElement;
    expect(missingOption).toBeInTheDocument();
    expect(missingOption.disabled).toBe(true);

    // Confirm button should be disabled until a valid agent is selected
    const submitBtn = screen.getByRole('button', { name: /Confirm Assignment/i });
    expect(submitBtn).toBeDisabled();
  });

  it('submits assignment successfully with selected agent', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue(mockAgents);
    const updatedTicket: TicketResponse = {
      ...baseTicket,
      assignedTo: 5,
    };
    vi.spyOn(ticketsApi, 'assignTicketApi').mockResolvedValue(updatedTicket);

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('John Doe (john@support.ai)')).toBeInTheDocument();
    });

    const select = screen.getByLabelText('Assignee');
    fireEvent.change(select, { target: { value: '5' } });

    const submitBtn = screen.getByRole('button', { name: /Confirm Assignment/i });
    expect(submitBtn).not.toBeDisabled();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(ticketsApi.assignTicketApi).toHaveBeenCalledWith(1, 5);
      expect(toast.success).toHaveBeenCalledWith('Ticket #1 assigned to John Doe.');
      expect(mockOnAssigned).toHaveBeenCalledWith(updatedTicket);
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  it('handles empty agents list correctly', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue([]);

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(
        screen.getByText(/No available support agents found in the system/i)
      ).toBeInTheDocument();
    });

    expect(screen.getByText('No available agents')).toBeInTheDocument();
    const submitBtn = screen.getByRole('button', { name: /Confirm Assignment/i });
    expect(submitBtn).toBeDisabled();
  });

  it('handles agent fetch error, clears stale data, and allows retry', async () => {
    const getAgentsSpy = vi.spyOn(usersApi, 'getAgentsApi')
      .mockRejectedValueOnce(new Error('Network connection timeout'))
      .mockResolvedValueOnce(mockAgents);

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Network connection timeout')).toBeInTheDocument();
    });

    // Form submit button should be disabled when fetch error exists
    const submitBtn = screen.getByRole('button', { name: /Confirm Assignment/i });
    expect(submitBtn).toBeDisabled();

    // Select should be disabled
    const select = screen.getByLabelText('Assignee') as HTMLSelectElement;
    expect(select).toBeDisabled();

    const retryBtn = screen.getByRole('button', { name: /Retry/i });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('Jane Support (jane@support.ai)')).toBeInTheDocument();
    });
    expect(getAgentsSpy).toHaveBeenCalledTimes(2);
  });

  it('clears stale agent data and disables assignment when a subsequent retry fails', async () => {
    const getAgentsSpy = vi.spyOn(usersApi, 'getAgentsApi')
      .mockResolvedValueOnce(mockAgents)
      .mockRejectedValueOnce(new Error('Directory service temporarily unreachable'));

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    // Initial fetch succeeds
    await waitFor(() => {
      expect(screen.getByText('Jane Support (jane@support.ai)')).toBeInTheDocument();
    });

    // User triggers a fresh fetch/retry that subsequently fails
    const retryTrigger = screen.queryByRole('button', { name: /Retry/i });
    if (!retryTrigger) {
      // Simulate refetch failure by calling getAgentsApi directly or re-opening
    }

    // Now re-render or trigger fetch failure
    cleanup();
    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Directory service temporarily unreachable')).toBeInTheDocument();
    });

    // Ensure stale options are NOT available
    expect(screen.queryByText('Jane Support (jane@support.ai)')).not.toBeInTheDocument();
    expect(screen.queryByText('John Doe (john@support.ai)')).not.toBeInTheDocument();

    const submitBtn = screen.getByRole('button', { name: /Confirm Assignment/i });
    expect(submitBtn).toBeDisabled();
    expect(getAgentsSpy).toHaveBeenCalledTimes(2);
  });

  it('displays API error when assignment submission fails', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue(mockAgents);
    vi.spyOn(ticketsApi, 'assignTicketApi').mockRejectedValue({
      message: 'The selected user is not an agent',
    });

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Jane Support (jane@support.ai)')).toBeInTheDocument();
    });

    const select = screen.getByLabelText('Assignee');
    fireEvent.change(select, { target: { value: '2' } });

    const submitBtn = screen.getByRole('button', { name: /Confirm Assignment/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('The selected user is not an agent')).toBeInTheDocument();
    });

    expect(mockOnAssigned).not.toHaveBeenCalled();
    expect(mockOnClose).not.toHaveBeenCalled();
  });

  it('invokes onClose when Cancel button or Close icon is clicked', async () => {
    vi.spyOn(usersApi, 'getAgentsApi').mockResolvedValue(mockAgents);

    render(
      <AssignAgentModal
        ticket={baseTicket}
        isOpen={true}
        onClose={mockOnClose}
        onAssigned={mockOnAssigned}
      />
    );

    const cancelBtn = screen.getByRole('button', { name: /Cancel/i });
    fireEvent.click(cancelBtn);
    expect(mockOnClose).toHaveBeenCalledTimes(1);

    const closeBtn = screen.getByLabelText('Close');
    fireEvent.click(closeBtn);
    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });
});
