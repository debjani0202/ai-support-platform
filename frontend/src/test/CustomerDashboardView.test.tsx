import { describe, expect, it, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CustomerDashboardView } from '@/components/dashboard/CustomerDashboardView';
import * as ticketsApi from '@/lib/api/tickets';

vi.mock('@/lib/api/tickets');

describe('CustomerDashboardView', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  const mockUser = {
    name: 'Debjani Mondal',
    email: 'debjani8650@gmail.com',
    role: 'CUSTOMER',
    userId: 12,
  };

  const mockTickets = [
    {
      id: 38,
      title: 'Unable to Log In to My Account',
      description: 'Password reset email is not arriving',
      status: 'OPEN' as const,
      priority: 'MEDIUM' as const,
      category: 'ACCOUNT_ACCESS' as const,
      createdBy: 12,
      assignedTo: null,
      createdAt: '2026-10-26T12:00:00Z',
      updatedAt: '2026-10-26T12:00:00Z',
    },
  ];

  it('renders top navbar with brand logo and rounded navigation pills', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
          initialTickets={mockTickets}
        />
      </MemoryRouter>
    );

    expect(screen.getAllByText('AI Support Platform').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Dashboard').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('My Tickets').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Submit Ticket').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Debjani Mondal').length).toBeGreaterThanOrEqual(1);
  });

  it('renders blue-purple gradient hero with dynamic greeting and floating badges', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText(/Welcome back,/i)).toBeDefined();
    expect(screen.getByText(/Debjani Mondal!/i)).toBeDefined();
    expect(
      screen.getByText(/AI-Powered Support Ticket Management Platform/i)
    ).toBeDefined();
    expect(
      screen.getByText(/Create, track, and resolve your support requests with the help of AI/i)
    ).toBeDefined();
    expect(screen.getByText('AI Classifies')).toBeDefined();
    expect(screen.getByText('Faster')).toBeDefined();
    expect(screen.getByText('Your Support,')).toBeDefined();
  });

  it('renders the four pastel ticket statistics cards with accurate counts', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 10, open: 4, inProgress: 3, resolved: 3 }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Total Tickets')).toBeDefined();
    expect(screen.getByText('Open Tickets')).toBeDefined();
    expect(screen.getByText('In Progress')).toBeDefined();
    expect(screen.getByText('Resolved')).toBeDefined();
    expect(screen.getByText('10')).toBeDefined();
    expect(screen.getByText('4')).toBeDefined();
    expect(screen.getAllByText('3').length).toBeGreaterThanOrEqual(2);
  });

  it('renders the two illustrated action cards linking to tickets and ticket creation', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('View My Support Tickets')).toBeDefined();
    expect(screen.getByText('Submit a New Ticket')).toBeDefined();
    expect(screen.getByText('View Tickets')).toBeDefined();
    expect(screen.getByText('Create Ticket')).toBeDefined();
  });

  it('renders recent tickets section with table when tickets exist', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
          initialTickets={mockTickets}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Recent Tickets')).toBeDefined();
    expect(screen.getAllByText('#38').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Unable to Log In to My Account').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Open').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Medium').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Account Access').length).toBeGreaterThanOrEqual(1);
  });

  it('renders clean empty state when no recent tickets exist', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 0, open: 0, inProgress: 0, resolved: 0 }}
          initialTickets={[]}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('No support tickets yet')).toBeDefined();
    expect(screen.getByText('Create Your First Ticket')).toBeDefined();
  });

  it('renders Support Made Smarter with AI workflow and compact feature cards', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    // Section Badge and Heading
    expect(screen.getByText('How It Works')).toBeDefined();
    expect(screen.getByText(/Support Made Smarter/i)).toBeDefined();
    expect(screen.getByText(/with AI/i)).toBeDefined();

    // 4-Step Connected Workflow
    expect(screen.getByText(/Your Issue/i)).toBeDefined();
    expect(screen.getByText(/AI Analyzes/i)).toBeDefined();
    expect(screen.getByText(/Available/i)).toBeDefined();
    expect(screen.getByText(/in the Agent Queue/i)).toBeDefined();
    expect(screen.getAllByText(/Resolution/i).length).toBeGreaterThanOrEqual(1);

    // 4 Compact Feature Cards
    expect(screen.getByText('AI-Powered Classification')).toBeDefined();
    expect(screen.getByText('Faster Response Times')).toBeDefined();
    expect(screen.getByText('Better Support Experience')).toBeDefined();
    expect(screen.getByText(/Secure & Reliable/i)).toBeDefined();
  });

  it('toggles mobile navigation drawer and handles escape key closing', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 0, open: 0, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    const toggleButton = screen.getByLabelText('Toggle navigation menu');
    expect(toggleButton.getAttribute('aria-expanded')).toBe('false');

    // Open mobile menu
    fireEvent.click(toggleButton);
    expect(toggleButton.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText('debjani8650@gmail.com')).toBeDefined();
    expect(screen.getByText('Change Password')).toBeDefined();
    expect(screen.getByText('Sign Out')).toBeDefined();

    // Press Escape to close
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(toggleButton.getAttribute('aria-expanded')).toBe('false');
  });

  it('renders authenticated footer with verified internal links and dynamic copyright', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 0, open: 0, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    const footerNav = screen.getByLabelText('Footer Navigation');
    expect(footerNav).toBeDefined();

    const currentYear = new Date().getFullYear().toString();
    expect(screen.getByText(new RegExp(currentYear))).toBeDefined();
    expect(screen.getByText(/All rights reserved/i)).toBeDefined();
    expect(screen.getAllByText('Notifications').length).toBeGreaterThanOrEqual(1);
  });

  it('renders role-specific navigation and action cards for AGENT role', () => {
    const mockAgent = {
      name: 'Agent Sarah',
      email: 'agent.sarah@support.ai',
      role: 'AGENT',
      userId: 55,
    };

    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockAgent}
          isPreview={true}
          initialStats={{ total: 5, open: 2, inProgress: 2, resolved: 1 }}
        />
      </MemoryRouter>
    );

    // Agent navigation should show "Assigned Tickets" and "Submit Ticket"
    expect(screen.getAllByText('Assigned Tickets').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Submit Ticket').length).toBeGreaterThanOrEqual(1);

    // Agent quick-action card should show "View Assigned Tickets" (header & button)
    expect(screen.getAllByText('View Assigned Tickets').length).toBe(2);
    expect(screen.getByText('Submit a New Ticket')).toBeDefined();
  });

  it('renders role-specific navigation and hides ticket creation actions for ADMIN role', () => {
    const mockAdmin = {
      name: 'Admin Chief',
      email: 'admin@support.ai',
      role: 'ADMIN',
      userId: 1,
    };

    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockAdmin}
          isPreview={true}
          initialStats={{ total: 20, open: 8, inProgress: 5, resolved: 7 }}
        />
      </MemoryRouter>
    );

    // Admin navigation should show "All Tickets" and "Agent Requests"
    expect(screen.getAllByText('All Tickets').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Agent Requests').length).toBeGreaterThanOrEqual(1);

    // Admin should NOT see "Submit Ticket" in nav or "Submit a New Ticket" action card
    expect(screen.queryByText('Submit Ticket')).toBeNull();
    expect(screen.queryByText('Submit a New Ticket')).toBeNull();

    // Admin quick-action card should show "View All Tickets" (header & button)
    expect(screen.getAllByText('View All Tickets').length).toBe(2);
  });

  it('renders Profile and Change Password links in mobile navigation menu', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    const toggleButton = screen.getByLabelText('Toggle navigation menu');
    fireEvent.click(toggleButton);

    expect(screen.getByText('Profile')).toBeDefined();
    expect(screen.getByText('Change Password')).toBeDefined();
  });

  it('calls getTicketStatsApi and displays stats cards with returned values', async () => {
    vi.mocked(ticketsApi.getTicketStatsApi).mockResolvedValue({
      total: 10,
      open: 4,
      inProgress: 3,
      resolved: 3,
    });
    vi.mocked(ticketsApi.getTicketsApi).mockResolvedValue({
      content: mockTickets,
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={false}
        />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(ticketsApi.getTicketStatsApi).toHaveBeenCalledTimes(1);
    });

    expect(ticketsApi.getTicketsApi).toHaveBeenCalledWith({
      page: 0,
      size: 5,
      sortBy: 'createdAt',
      direction: 'desc',
    });

    // The statistics cards display the returned values
    expect(await screen.findByText('10')).toBeDefined();
    expect(screen.getByText('4')).toBeDefined();
    expect(screen.getAllByText('3').length).toBeGreaterThanOrEqual(2);

    // Recent ticket still loads using existing API
    expect((await screen.findAllByText('#38')).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Unable to Log In to My Account').length).toBeGreaterThanOrEqual(1);
  });

  it('handles statistics fetch failure and follows existing error-handling behavior', async () => {
    vi.mocked(ticketsApi.getTicketStatsApi).mockRejectedValue(
      new Error('Failed to fetch stats')
    );
    vi.mocked(ticketsApi.getTicketsApi).mockResolvedValue({
      content: mockTickets,
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={false}
        />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(ticketsApi.getTicketStatsApi).toHaveBeenCalledTimes(1);
    });

    // Stats error alert notification is displayed
    const alert = await screen.findByRole('alert');
    expect(alert).toBeDefined();
    expect(
      screen.getByText(/Unable to load current ticket statistics/i)
    ).toBeDefined();

    // Stats cards show unavailable placeholder
    const dashes = screen.getAllByTitle('Data unavailable');
    expect(dashes.length).toBe(4);

    // Recent tickets still load successfully
    expect((await screen.findAllByText('#38')).length).toBeGreaterThanOrEqual(1);
  });
});
