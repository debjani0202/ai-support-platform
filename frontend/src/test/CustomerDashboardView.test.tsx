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
    expect(screen.getAllByText('Tickets').length).toBeGreaterThanOrEqual(1);
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
});
