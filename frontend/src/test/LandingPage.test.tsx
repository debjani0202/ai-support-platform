import { describe, expect, it, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '@/context/auth-context';
import type { AuthContextType } from '@/context/AuthContext';
import { LandingPage } from '@/pages/public/LandingPage';

describe('LandingPage', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  const unauthContextValue: AuthContextType = {
    isInitializing: false,
    user: null,
    token: null,
    isAuthenticated: false,
    login: vi.fn(),
    logout: vi.fn(),
  };

  const authContextValue: AuthContextType = {
    isInitializing: false,
    user: {
      userId: 1,
      name: 'Debjani Mondal',
      email: 'debjani@example.com',
      role: 'CUSTOMER',
    },
    token: 'mock-jwt-token',
    isAuthenticated: true,
    login: vi.fn(),
    logout: vi.fn(),
  };

  const renderLandingPage = (authValue: AuthContextType = unauthContextValue) =>
    render(
      <AuthContext.Provider value={authValue}>
        <MemoryRouter>
          <LandingPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

  it('renders semantic landmarks: header, main, and contentinfo footer', () => {
    renderLandingPage();

    expect(screen.getByRole('banner')).toBeDefined();
    expect(screen.getByRole('main')).toBeDefined();
    expect(screen.getByRole('contentinfo')).toBeDefined();
  });

  it('renders navbar with BrandLogo and platform wordmark', () => {
    renderLandingPage();

    const logoElements = screen.getAllByLabelText(/AI Support Platform Home/i);
    expect(logoElements.length).toBeGreaterThanOrEqual(1);

    const platformWords = screen.getAllByText(/AI Support/i);
    expect(platformWords.length).toBeGreaterThanOrEqual(1);
  });

  it('renders main headline and hero description', () => {
    renderLandingPage();

    const h1 = screen.getByRole('heading', { level: 1 });
    expect(h1).toHaveTextContent(/AI-Powered/i);
    expect(h1).toHaveTextContent(/Support Ticket/i);
    expect(h1).toHaveTextContent(/Management/i);

    expect(
      screen.getByText(/Create, track, and resolve support tickets with the power of AI/i)
    ).toBeDefined();
  });

  it('renders the hero typing badge component', () => {
    renderLandingPage();

    const badgeText = screen.getByText(/Smarter Support/i);
    expect(badgeText).toBeDefined();
  });

  it('renders Get Started and Sign In buttons linking to /login for unauthenticated guests', () => {
    renderLandingPage(unauthContextValue);

    const getStartedLinks = screen.getAllByRole('link', { name: /Get Started/i });
    expect(getStartedLinks.length).toBeGreaterThanOrEqual(2);
    getStartedLinks.forEach((link) => {
      expect(link.getAttribute('href')).toBe('/login');
    });

    const signInLinks = screen.getAllByRole('link', { name: /Sign In/i });
    expect(signInLinks.length).toBeGreaterThanOrEqual(2);
    signInLinks.forEach((link) => {
      expect(link.getAttribute('href')).toBe('/login');
    });
  });

  it('renders Go to Dashboard button when user is authenticated', () => {
    renderLandingPage(authContextValue);

    const dashboardLinks = screen.getAllByRole('link', { name: /Go to Dashboard/i });
    expect(dashboardLinks.length).toBeGreaterThanOrEqual(1);
    expect(dashboardLinks[0].getAttribute('href')).toBe('/dashboard');
  });

  it('renders 3 Key Benefits items', () => {
    renderLandingPage();

    expect(screen.getByLabelText(/AI-Assisted Ticket Classification/i)).toBeDefined();
    expect(screen.getByLabelText(/Real-Time Ticket Tracking/i)).toBeDefined();
    expect(screen.getByLabelText(/Secure & Reliable Support Management/i)).toBeDefined();
  });

  it('renders 4 Feature cards including fallback ticket icon and learn more links', () => {
    renderLandingPage();

    expect(screen.getByText('Smart Ticket Management')).toBeDefined();
    expect(screen.getByText('AI-Powered Assistance')).toBeDefined();
    expect(screen.getByText('Real-Time Notifications')).toBeDefined();
    expect(screen.getAllByText('Secure & Reliable').length).toBeGreaterThanOrEqual(1);

    const learnMoreLinks = screen.getAllByRole('link', { name: /Learn more/i });
    expect(learnMoreLinks.length).toBe(4);
    learnMoreLinks.forEach((link) => {
      expect(link.getAttribute('href')).toBe('/login');
    });
  });

  it('renders 3 How It Works workflow steps', () => {
    renderLandingPage();

    expect(screen.getAllByText('Submit a Ticket').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Get AI Assistance')).toBeDefined();
    expect(screen.getByText('Track & Resolve')).toBeDefined();
  });

  it('renders bottom CTA banner with Simplify your support process today', () => {
    renderLandingPage();

    expect(screen.getByText(/Simplify your support process today/i)).toBeDefined();
    expect(screen.getByText(/Ready to get started\?/i)).toBeDefined();
  });

  it('renders honest footer with copyright and excludes Follow Us or social media icons', () => {
    renderLandingPage();

    expect(screen.getByText(/© 2026 AI Support Platform\. All rights reserved\./i)).toBeDefined();
    expect(screen.queryByText(/Follow Us/i)).toBeNull();
    expect(screen.queryByText(/Twitter|LinkedIn|GitHub|Facebook|Instagram/i)).toBeNull();
  });
});
