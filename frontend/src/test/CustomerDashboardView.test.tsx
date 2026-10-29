import { describe, expect, it, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CustomerDashboardView } from '@/components/dashboard/CustomerDashboardView';

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
      id: 101,
      title: 'Network latency in US East',
      description: 'Experiencing high packet drop',
      status: 'OPEN' as const,
      priority: 'HIGH' as const,
      category: 'TECHNICAL_ISSUE' as const,
      createdBy: 12,
      assignedTo: null,
      createdAt: '2026-10-26T12:00:00Z',
      updatedAt: '2026-10-26T12:00:00Z',
    },
  ];

  it('renders top horizontal navbar with brand logo and core navigation items', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 5, open: 2, inProgress: 1, resolved: 2 }}
          initialTickets={mockTickets}
        />
      </MemoryRouter>
    );

    expect(screen.getAllByText('AI Support Platform').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Dashboard').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Tickets').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Submit Ticket').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Debjani Mondal').length).toBeGreaterThanOrEqual(1);
  });

  it('renders welcome hero heading with dynamic user name and platform overview', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 5, open: 2, inProgress: 1, resolved: 2 }}
        />
      </MemoryRouter>
    );

    const welcomeElements = screen.getAllByText(/Welcome back, Debjani Mondal!/);
    expect(welcomeElements.length).toBeGreaterThanOrEqual(1);
    expect(
      screen.getByText(/AI Support Ticket Management Platform/i)
    ).toBeDefined();
    expect(
      screen.getByText(/Track your support inquiries in real time/i)
    ).toBeDefined();
  });

  it('renders the four ticket statistics cards with accurate counts', () => {
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

  it('renders the two primary action cards linking to tickets and ticket creation', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={true}
          initialStats={{ total: 1, open: 1, inProgress: 0, resolved: 0 }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('My Support Tickets')).toBeDefined();
    expect(screen.getByText('Submit New Ticket')).toBeDefined();
    expect(screen.getByText('View Tickets')).toBeDefined();
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
    expect(screen.getAllByText('#101').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Network latency in US East').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Open').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('High').length).toBeGreaterThanOrEqual(1);
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

    // Click to open mobile navigation
    fireEvent.click(toggleButton);
    expect(toggleButton.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText('debjani8650@gmail.com')).toBeDefined();
    expect(screen.getByText('Change Password')).toBeDefined();
    expect(screen.getByText('Sign Out')).toBeDefined();

    // Press Escape to close mobile menu
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
});
