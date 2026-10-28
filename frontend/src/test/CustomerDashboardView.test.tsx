import { describe, expect, it, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CustomerDashboardView } from '@/components/dashboard/CustomerDashboardView';

describe('CustomerDashboardView', () => {
  afterEach(() => {
    cleanup();
  });

  const mockUser = {
    name: 'Debjani Mondal',
    email: 'debjani8650@gmail.com',
    role: 'CUSTOMER',
    userId: 12,
  };

  it('renders welcome heading and subtitle', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView user={mockUser} isPreview={false} />
      </MemoryRouter>
    );

    const welcomeElements = screen.getAllByText(/Welcome back, Debjani Mondal!/);
    expect(welcomeElements.length).toBeGreaterThanOrEqual(1);
    expect(
      screen.getByText(/AI Support Ticket Management Platform - Dashboard Overview/)
    ).toBeDefined();
  });

  it('renders all navigation items in sidebar and quick pills', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView user={mockUser} isPreview={false} />
      </MemoryRouter>
    );

    expect(screen.getAllByText('Dashboard').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Tickets').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Submit Ticket').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Notifications').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Change Password').length).toBeGreaterThanOrEqual(1);
  });

  it('renders the four ticket statistics cards with counts', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={false}
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
  });

  it('renders the three pastel feature cards', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView user={mockUser} isPreview={false} />
      </MemoryRouter>
    );

    expect(screen.getByText('My Support Tickets')).toBeDefined();
    expect(screen.getByText('Submit New Ticket')).toBeDefined();
    expect(screen.getAllByText('Notifications').length).toBeGreaterThanOrEqual(1);
  });

  it('renders recent tickets section with table when tickets exist', () => {
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

    render(
      <MemoryRouter>
        <CustomerDashboardView
          user={mockUser}
          isPreview={false}
          initialTickets={mockTickets}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Recent Tickets')).toBeDefined();
    expect(screen.getByText('#101')).toBeDefined();
    expect(screen.getByText('Network latency in US East')).toBeDefined();
  });

  it('renders session & role information panel with user details', () => {
    render(
      <MemoryRouter>
        <CustomerDashboardView user={mockUser} isPreview={false} />
      </MemoryRouter>
    );

    expect(screen.getByText('Session & Role Information')).toBeDefined();
    expect(screen.getByText('FULL NAME')).toBeDefined();
    expect(screen.getAllByText('Debjani Mondal').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('EMAIL ADDRESS')).toBeDefined();
    expect(screen.getByText('debjani8650@gmail.com')).toBeDefined();
    expect(screen.getByText(/Session active with Role/)).toBeDefined();
  });
});
