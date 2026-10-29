import { useState, useEffect, useCallback } from 'react';
import { Link, useLocation } from 'react-router-dom';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import {
  Ticket,
  PlusCircle,
  KeyRound,
  LogOut,
  ArrowRight,
  Clock,
  RefreshCw,
  Menu,
  X,
  Inbox,
  AlertCircle,
  ChevronDown,
  Sparkles,
  Layers,
  CheckCircle2,
} from 'lucide-react';
import { BrandLogo } from '@/components/common/BrandLogo';
import { StatusBadge } from '@/components/badges/StatusBadge';
import { PriorityBadge } from '@/components/badges/PriorityBadge';
import { CategoryBadge } from '@/components/badges/CategoryBadge';
import { NotificationBell } from '@/components/notifications/NotificationBell';
import { getTicketsApi } from '@/lib/api/tickets';
import type { TicketResponse } from '@/types/ticket.types';
import type { NotificationResponse } from '@/types/notification.types';
import { formatDistanceToNow, parseISO } from 'date-fns';

export interface DashboardUser {
  name: string;
  email: string;
  role: string;
  userId?: number;
}

export interface TicketStats {
  total: number;
  open: number;
  inProgress: number;
  resolved: number;
}

export interface CustomerDashboardViewProps {
  user: DashboardUser;
  unreadCount?: number;
  isPreview?: boolean;
  onLogout?: () => void;
  showToast?: boolean;
  initialStats?: TicketStats;
  initialTickets?: TicketResponse[];
  initialNotifications?: NotificationResponse[];
}

export function CustomerDashboardView({
  user,
  isPreview = false,
  onLogout,
  initialStats,
  initialTickets,
}: CustomerDashboardViewProps) {
  const location = useLocation();

  // Navigation states
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  // Statistics states
  const [stats, setStats] = useState<TicketStats | null>(initialStats ?? null);
  const [isLoadingStats, setIsLoadingStats] = useState<boolean>(
    !initialStats && !isPreview
  );
  const [statsError, setStatsError] = useState<boolean>(false);

  // Recent tickets states
  const [recentTickets, setRecentTickets] = useState<TicketResponse[]>(
    initialTickets ?? []
  );
  const [isLoadingTickets, setIsLoadingTickets] = useState<boolean>(
    !initialTickets && !isPreview
  );
  const [ticketsError, setTicketsError] = useState<boolean>(false);

  // Data fetching callback
  const loadDashboardData = useCallback(async () => {
    if (isPreview) return;

    setIsLoadingStats(true);
    setIsLoadingTickets(true);
    setStatsError(false);
    setTicketsError(false);

    try {
      // 4 concurrent queries using getTicketsApi
      // Query 1: Total tickets + 5 most recent tickets
      // Query 2: Total OPEN tickets
      // Query 3: Total IN_PROGRESS tickets
      // Query 4: Total RESOLVED tickets
      const [allTicketsRes, openRes, inProgressRes, resolvedRes] =
        await Promise.all([
          getTicketsApi({
            page: 0,
            size: 5,
            sortBy: 'createdAt',
            direction: 'desc',
          }),
          getTicketsApi({ status: 'OPEN', size: 1 }),
          getTicketsApi({ status: 'IN_PROGRESS', size: 1 }),
          getTicketsApi({ status: 'RESOLVED', size: 1 }),
        ]);

      setStats({
        total: allTicketsRes.totalElements,
        open: openRes.totalElements,
        inProgress: inProgressRes.totalElements,
        resolved: resolvedRes.totalElements,
      });
      setRecentTickets(allTicketsRes.content);
    } catch {
      setStatsError(true);
      setTicketsError(true);
    } finally {
      setIsLoadingStats(false);
      setIsLoadingTickets(false);
    }
  }, [isPreview]);

  // Initial fetch on mount
  useEffect(() => {
    if (!initialStats || !initialTickets) {
      loadDashboardData();
    }
  }, [initialStats, initialTickets, loadDashboardData]);

  // Accessibility: Close mobile nav on Escape key press
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setMobileNavOpen(false);
      }
    };
    if (mobileNavOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [mobileNavOpen]);

  // Safe time formatting helper
  const formatTimeAgo = (dateStr: string) => {
    try {
      return formatDistanceToNow(parseISO(dateStr), { addSuffix: true });
    } catch {
      return dateStr;
    }
  };

  const displayName = user.name?.trim() || 'Customer';
  const userInitial = displayName.charAt(0).toUpperCase();

  return (
    <div className="min-h-screen bg-slate-50/70 flex flex-col font-sans text-slate-800 antialiased selection:bg-indigo-100 selection:text-indigo-900">
      {/* ========================================================================= */}
      {/* 1. TOP HORIZONTAL NAVBAR                                                  */}
      {/* ========================================================================= */}
      <header className="sticky top-0 z-30 w-full bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
          {/* Left: Brand Logo & Title */}
          <div className="flex items-center gap-3">
            <Link
              to="/dashboard"
              className="flex items-center gap-2.5 group focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 rounded-lg p-1"
              aria-label="AI Support Platform Dashboard"
            >
              <BrandLogo className="w-8 h-8 rounded-lg shadow-xs transition-transform group-hover:scale-105" />
              <span className="text-base sm:text-lg font-bold bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-800 bg-clip-text text-transparent">
                AI Support Platform
              </span>
            </Link>
          </div>

          {/* Center: Desktop Navigation Links */}
          <nav
            aria-label="Main Navigation"
            className="hidden md:flex items-center gap-1.5"
          >
            <Link
              to="/dashboard"
              className={`px-3.5 py-1.5 rounded-lg text-sm font-semibold transition-colors ${
                location.pathname === '/dashboard'
                  ? 'bg-indigo-50 text-indigo-700 border border-indigo-100 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80'
              }`}
            >
              Dashboard
            </Link>
            <Link
              to="/tickets"
              className={`px-3.5 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                location.pathname === '/tickets'
                  ? 'bg-indigo-50 text-indigo-700 border border-indigo-100 shadow-xs font-semibold'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80'
              }`}
            >
              Tickets
            </Link>
            <Link
              to="/tickets/new"
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-sm font-medium text-indigo-600 hover:text-indigo-700 hover:bg-indigo-50/70 border border-indigo-200/70 transition-colors"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Submit Ticket</span>
            </Link>
          </nav>

          {/* Right: Notification Bell & User Profile Dropdown */}
          <div className="flex items-center gap-2 sm:gap-3">
            {/* Reused NotificationBell component */}
            <NotificationBell />

            {/* Profile Dropdown */}
            <DropdownMenu.Root>
              <DropdownMenu.Trigger asChild>
                <button
                  type="button"
                  aria-label="Open user menu"
                  className="flex items-center gap-2 p-1 rounded-full sm:rounded-lg sm:px-2.5 sm:py-1.5 hover:bg-slate-100 text-slate-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                >
                  <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-600 to-purple-600 text-white font-bold text-xs flex items-center justify-center shadow-xs border border-white">
                    {userInitial}
                  </div>
                  <span className="hidden sm:inline text-sm font-semibold text-slate-800 max-w-[130px] truncate">
                    {displayName}
                  </span>
                  <ChevronDown className="hidden sm:inline w-3.5 h-3.5 text-slate-400" />
                </button>
              </DropdownMenu.Trigger>

              <DropdownMenu.Portal>
                <DropdownMenu.Content
                  align="end"
                  sideOffset={8}
                  className="w-56 rounded-xl bg-white p-1.5 shadow-xl border border-slate-200 z-50 animate-in fade-in zoom-in-95 duration-150 focus:outline-none"
                >
                  <div className="px-3 py-2 border-b border-slate-100">
                    <p className="text-xs font-bold text-slate-900 truncate">
                      {displayName}
                    </p>
                    <p className="text-[11px] text-slate-500 truncate">
                      {user.email || 'Customer Account'}
                    </p>
                    <span className="mt-1.5 inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-100">
                      {user.role || 'CUSTOMER'}
                    </span>
                  </div>

                  <DropdownMenu.Item asChild>
                    <Link
                      to="/change-password"
                      className="flex items-center gap-2 px-3 py-2 text-xs font-medium text-slate-700 rounded-lg hover:bg-slate-100 hover:text-slate-900 cursor-pointer outline-none transition-colors"
                    >
                      <KeyRound className="w-3.5 h-3.5 text-slate-400" />
                      <span>Change Password</span>
                    </Link>
                  </DropdownMenu.Item>

                  <DropdownMenu.Separator className="h-px bg-slate-100 my-1" />

                  <DropdownMenu.Item asChild>
                    <button
                      type="button"
                      onClick={onLogout}
                      className="w-full flex items-center gap-2 px-3 py-2 text-xs font-medium text-rose-600 rounded-lg hover:bg-rose-50 cursor-pointer outline-none transition-colors"
                    >
                      <LogOut className="w-3.5 h-3.5 text-rose-500" />
                      <span>Sign Out</span>
                    </button>
                  </DropdownMenu.Item>
                </DropdownMenu.Content>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>

            {/* Mobile Hamburger Button */}
            <button
              type="button"
              onClick={() => setMobileNavOpen((prev) => !prev)}
              aria-label="Toggle navigation menu"
              aria-expanded={mobileNavOpen}
              aria-controls="mobile-nav-panel"
              className="md:hidden p-2 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              {mobileNavOpen ? (
                <X className="w-5 h-5" />
              ) : (
                <Menu className="w-5 h-5" />
              )}
            </button>
          </div>
        </div>

        {/* Mobile Navigation Drawer / Panel */}
        {mobileNavOpen && (
          <div
            id="mobile-nav-panel"
            className="md:hidden border-t border-slate-200/80 bg-white/98 px-4 pt-3 pb-5 shadow-lg space-y-3 animate-in slide-in-from-top-2 duration-150"
          >
            <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
              <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-indigo-600 to-purple-600 text-white font-bold text-sm flex items-center justify-center shadow-xs">
                {userInitial}
              </div>
              <div className="min-w-0">
                <p className="text-sm font-bold text-slate-900 truncate">
                  {displayName}
                </p>
                <p className="text-xs text-slate-500 truncate">{user.email}</p>
              </div>
            </div>

            <nav aria-label="Mobile Navigation" className="space-y-1">
              <Link
                to="/dashboard"
                onClick={() => setMobileNavOpen(false)}
                className={`block px-3 py-2 rounded-lg text-sm font-medium ${
                  location.pathname === '/dashboard'
                    ? 'bg-indigo-50 text-indigo-700 font-semibold'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                Dashboard
              </Link>
              <Link
                to="/tickets"
                onClick={() => setMobileNavOpen(false)}
                className={`block px-3 py-2 rounded-lg text-sm font-medium ${
                  location.pathname === '/tickets'
                    ? 'bg-indigo-50 text-indigo-700 font-semibold'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                Tickets
              </Link>
              <Link
                to="/tickets/new"
                onClick={() => setMobileNavOpen(false)}
                className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-indigo-600 hover:bg-indigo-50"
              >
                <PlusCircle className="w-4 h-4" />
                <span>Submit Ticket</span>
              </Link>
              <div className="pt-2 border-t border-slate-100" />
              <Link
                to="/change-password"
                onClick={() => setMobileNavOpen(false)}
                className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                <KeyRound className="w-4 h-4 text-slate-400" />
                <span>Change Password</span>
              </Link>
              <button
                type="button"
                onClick={() => {
                  setMobileNavOpen(false);
                  onLogout?.();
                }}
                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-rose-600 hover:bg-rose-50"
              >
                <LogOut className="w-4 h-4 text-rose-500" />
                <span>Sign Out</span>
              </button>
            </nav>
          </div>
        )}
      </header>

      {/* ========================================================================= */}
      {/* 2. MAIN DASHBOARD CONTENT                                                 */}
      {/* ========================================================================= */}
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        {/* Welcome Hero Banner */}
        <section
          aria-labelledby="welcome-heading"
          className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-indigo-50/90 via-purple-50/60 to-blue-50/80 border border-indigo-100/80 shadow-xs p-6 sm:p-8 mb-8"
        >
          {/* Decorative ambient background visual */}
          <div
            className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 rounded-full bg-gradient-to-br from-indigo-200/30 to-purple-200/30 blur-2xl pointer-events-none"
            aria-hidden="true"
          />
          <div
            className="absolute bottom-0 right-10 translate-y-1/4 opacity-10 pointer-events-none hidden sm:block"
            aria-hidden="true"
          >
            <Layers className="w-48 h-48 text-indigo-700" />
          </div>

          <div className="relative z-10">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-white/80 text-indigo-700 border border-indigo-200/60 shadow-xs mb-3">
              <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
              <span>AI-Powered Customer Portal</span>
            </div>
            <h1
              id="welcome-heading"
              className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight"
            >
              Welcome back, {displayName}! 👋
            </h1>
            <p className="mt-1 text-sm sm:text-base font-semibold text-indigo-950/90">
              AI Support Ticket Management Platform — Dashboard Overview
            </p>
            <p className="mt-2 text-sm text-slate-600 max-w-2xl leading-relaxed">
              Track your support inquiries in real time, monitor automated
              triage updates, or quickly submit a new ticket for rapid
              resolution.
            </p>
          </div>
        </section>

        {/* Global Stats Error Notification (if failed) */}
        {statsError && (
          <div
            role="alert"
            className="mb-6 p-4 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-between gap-3 text-amber-800"
          >
            <div className="flex items-center gap-2.5">
              <AlertCircle className="w-5 h-5 text-amber-600 shrink-0" />
              <p className="text-sm font-medium">
                Unable to load current ticket statistics. Please check your
                connection.
              </p>
            </div>
            <button
              type="button"
              onClick={loadDashboardData}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-amber-100 hover:bg-amber-200 text-amber-900 transition-colors shrink-0"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Retry</span>
            </button>
          </div>
        )}

        {/* ========================================================================= */}
        {/* 3. FOUR TICKET SUMMARY CARDS                                              */}
        {/* ========================================================================= */}
        <section
          aria-label="Ticket Overview Statistics"
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5 mb-8"
        >
          {/* Card 1: TOTAL TICKETS */}
          <div className="rounded-xl bg-white border border-slate-200/90 p-5 shadow-xs transition-shadow hover:shadow-md">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-purple-700">
                Total Tickets
              </span>
              <div className="w-9 h-9 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center border border-purple-100/70">
                <Ticket className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-purple-100/80 rounded animate-pulse" />
              ) : statsError ? (
                <span className="text-2xl font-bold text-slate-400" title="Data unavailable">
                  —
                </span>
              ) : (
                <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.total ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              All lifetime submitted tickets
            </p>
          </div>

          {/* Card 2: OPEN TICKETS */}
          <div className="rounded-xl bg-white border border-slate-200/90 p-5 shadow-xs transition-shadow hover:shadow-md">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-amber-700">
                Open Tickets
              </span>
              <div className="w-9 h-9 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center border border-amber-100/70">
                <Clock className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-amber-100/80 rounded animate-pulse" />
              ) : statsError ? (
                <span className="text-2xl font-bold text-slate-400" title="Data unavailable">
                  —
                </span>
              ) : (
                <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.open ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Awaiting review or triage
            </p>
          </div>

          {/* Card 3: IN PROGRESS */}
          <div className="rounded-xl bg-white border border-slate-200/90 p-5 shadow-xs transition-shadow hover:shadow-md">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-sky-700">
                In Progress
              </span>
              <div className="w-9 h-9 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center border border-sky-100/70">
                <RefreshCw className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-sky-100/80 rounded animate-pulse" />
              ) : statsError ? (
                <span className="text-2xl font-bold text-slate-400" title="Data unavailable">
                  —
                </span>
              ) : (
                <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.inProgress ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Currently being handled
            </p>
          </div>

          {/* Card 4: RESOLVED */}
          <div className="rounded-xl bg-white border border-slate-200/90 p-5 shadow-xs transition-shadow hover:shadow-md">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-emerald-700">
                Resolved
              </span>
              <div className="w-9 h-9 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-100/70">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-emerald-100/80 rounded animate-pulse" />
              ) : statsError ? (
                <span className="text-2xl font-bold text-slate-400" title="Data unavailable">
                  —
                </span>
              ) : (
                <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.resolved ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Successfully completed
            </p>
          </div>
        </section>

        {/* ========================================================================= */}
        {/* 4. TWO PRIMARY ACTION CARDS                                               */}
        {/* ========================================================================= */}
        <section
          aria-label="Quick Actions"
          className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-8"
        >
          {/* Action Card A: View My Support Tickets */}
          <div className="relative group rounded-2xl bg-white border border-slate-200/90 p-6 shadow-xs hover:shadow-md transition-all flex flex-col justify-between">
            <div>
              <div className="w-12 h-12 rounded-xl bg-purple-50 text-purple-600 border border-purple-100 flex items-center justify-center mb-4 transition-transform group-hover:scale-105">
                <Ticket className="w-6 h-6" />
              </div>
              <h2 className="text-lg font-bold text-slate-900">
                My Support Tickets
              </h2>
              <p className="mt-1.5 text-sm text-slate-600 leading-relaxed">
                Browse, filter, and track status updates on all submitted
                inquiries with real-time resolution history.
              </p>
            </div>
            <div className="mt-6">
              <Link
                to="/tickets"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold bg-purple-50 text-purple-700 border border-purple-200/80 hover:bg-purple-100/90 transition-colors"
              >
                <span>View Tickets</span>
                <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5" />
              </Link>
            </div>
          </div>

          {/* Action Card B: Submit a New Ticket */}
          <div className="relative group rounded-2xl bg-white border border-slate-200/90 p-6 shadow-xs hover:shadow-md transition-all flex flex-col justify-between">
            <div>
              <div className="w-12 h-12 rounded-xl bg-indigo-50 text-indigo-600 border border-indigo-100 flex items-center justify-center mb-4 transition-transform group-hover:scale-105">
                <PlusCircle className="w-6 h-6" />
              </div>
              <h2 className="text-lg font-bold text-slate-900">
                Submit New Ticket
              </h2>
              <p className="mt-1.5 text-sm text-slate-600 leading-relaxed">
                Experience automated AI triage, smart category prediction, and
                priority queue routing for urgent issues.
              </p>
            </div>
            <div className="mt-6">
              <Link
                to="/tickets/new"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold bg-indigo-600 text-white shadow-xs hover:bg-indigo-700 transition-colors"
              >
                <span>Submit Ticket</span>
                <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5" />
              </Link>
            </div>
          </div>
        </section>

        {/* ========================================================================= */}
        {/* 5. RECENT TICKETS SECTION                                                 */}
        {/* ========================================================================= */}
        <section
          aria-labelledby="recent-tickets-heading"
          className="rounded-2xl bg-white border border-slate-200/90 shadow-xs overflow-hidden mb-12"
        >
          {/* Section Header */}
          <div className="p-5 sm:px-6 flex items-center justify-between border-b border-slate-100">
            <div>
              <h2
                id="recent-tickets-heading"
                className="text-lg font-bold text-slate-900"
              >
                Recent Tickets
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Your latest submitted support requests
              </p>
            </div>
            <Link
              to="/tickets"
              className="inline-flex items-center gap-1.5 text-xs sm:text-sm font-semibold text-indigo-600 hover:text-indigo-800 transition-colors"
            >
              <span>View All</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>

          {/* Loading State */}
          {isLoadingTickets && (
            <div className="p-6 space-y-3">
              <div className="h-12 bg-slate-100/80 rounded-lg animate-pulse" />
              <div className="h-12 bg-slate-100/80 rounded-lg animate-pulse" />
              <div className="h-12 bg-slate-100/80 rounded-lg animate-pulse" />
            </div>
          )}

          {/* Error State */}
          {!isLoadingTickets && ticketsError && (
            <div className="p-8 text-center">
              <div className="w-12 h-12 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-3">
                <AlertCircle className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-900">
                Failed to load recent tickets
              </h3>
              <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1 mb-4">
                We encountered an error fetching your recent tickets. Please try
                again.
              </p>
              <button
                type="button"
                onClick={loadDashboardData}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 transition-colors shadow-xs"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Retry Loading Tickets</span>
              </button>
            </div>
          )}

          {/* Empty State */}
          {!isLoadingTickets && !ticketsError && recentTickets.length === 0 && (
            <div className="p-10 text-center">
              <div className="w-14 h-14 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-3.5 border border-indigo-100/60">
                <Inbox className="w-7 h-7" />
              </div>
              <h3 className="text-base font-bold text-slate-900">
                No support tickets yet
              </h3>
              <p className="text-xs sm:text-sm text-slate-500 max-w-md mx-auto mt-1 mb-5">
                You haven&apos;t created any tickets yet. Submit a new ticket to
                get started with fast, automated support.
              </p>
              <Link
                to="/tickets/new"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold bg-indigo-600 text-white hover:bg-indigo-700 transition-colors shadow-xs"
              >
                <PlusCircle className="w-4 h-4" />
                <span>Create Your First Ticket</span>
              </Link>
            </div>
          )}

          {/* Populated State — Desktop & Tablet Table */}
          {!isLoadingTickets && !ticketsError && recentTickets.length > 0 && (
            <div className="hidden sm:block overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="bg-slate-50/80 text-[11px] font-bold uppercase tracking-wider text-slate-500 border-b border-slate-200/80">
                  <tr>
                    <th scope="col" className="py-3 px-4 sm:px-6">
                      ID
                    </th>
                    <th scope="col" className="py-3 px-4 sm:px-6">
                      Title
                    </th>
                    <th scope="col" className="py-3 px-4 sm:px-6">
                      Category
                    </th>
                    <th scope="col" className="py-3 px-4 sm:px-6">
                      Priority
                    </th>
                    <th scope="col" className="py-3 px-4 sm:px-6">
                      Status
                    </th>
                    <th scope="col" className="py-3 px-4 sm:px-6">
                      Created
                    </th>
                    <th scope="col" className="py-3 px-4 sm:px-6 text-right">
                      Action
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {recentTickets.map((ticket) => (
                    <tr
                      key={ticket.id}
                      className="hover:bg-slate-50/70 transition-colors group"
                    >
                      <td className="py-3.5 px-4 sm:px-6 font-mono text-xs font-bold text-slate-500">
                        #{ticket.id}
                      </td>
                      <td className="py-3.5 px-4 sm:px-6">
                        <Link
                          to={`/tickets/${ticket.id}`}
                          className="font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors line-clamp-1"
                        >
                          {ticket.title}
                        </Link>
                      </td>
                      <td className="py-3.5 px-4 sm:px-6">
                        <CategoryBadge category={ticket.category} />
                      </td>
                      <td className="py-3.5 px-4 sm:px-6">
                        <PriorityBadge priority={ticket.priority} />
                      </td>
                      <td className="py-3.5 px-4 sm:px-6">
                        <StatusBadge status={ticket.status} />
                      </td>
                      <td className="py-3.5 px-4 sm:px-6 text-xs text-slate-500 whitespace-nowrap">
                        {formatTimeAgo(ticket.createdAt)}
                      </td>
                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <Link
                          to={`/tickets/${ticket.id}`}
                          className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition-colors"
                        >
                          <span>View</span>
                          <ArrowRight className="w-3.5 h-3.5" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Populated State — Mobile Stacked Cards (<640px) */}
          {!isLoadingTickets && !ticketsError && recentTickets.length > 0 && (
            <div className="sm:hidden divide-y divide-slate-100">
              {recentTickets.map((ticket) => (
                <div key={ticket.id} className="p-4 space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs font-bold text-slate-500">
                      #{ticket.id}
                    </span>
                    <StatusBadge status={ticket.status} />
                  </div>
                  <Link
                    to={`/tickets/${ticket.id}`}
                    className="block font-semibold text-sm text-slate-900 hover:text-indigo-600 transition-colors"
                  >
                    {ticket.title}
                  </Link>
                  <div className="flex items-center gap-2 pt-1">
                    <CategoryBadge category={ticket.category} />
                    <PriorityBadge priority={ticket.priority} />
                  </div>
                  <div className="flex items-center justify-between text-xs text-slate-500 pt-2 border-t border-slate-50">
                    <span>{formatTimeAgo(ticket.createdAt)}</span>
                    <Link
                      to={`/tickets/${ticket.id}`}
                      className="inline-flex items-center gap-1 font-semibold text-indigo-600"
                    >
                      <span>View details</span>
                      <ArrowRight className="w-3 h-3" />
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>

      {/* ========================================================================= */}
      {/* 6. AUTHENTICATED FOOTER                                                   */}
      {/* ========================================================================= */}
      <footer className="mt-auto border-t border-slate-200 bg-white py-8">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pb-6 border-b border-slate-100">
            {/* Brand Logo & Tagline */}
            <div className="flex items-center gap-2.5">
              <BrandLogo className="w-7 h-7 rounded-md shadow-xs" />
              <div>
                <span className="text-sm font-bold text-slate-900">
                  AI Support Platform
                </span>
                <p className="text-xs text-slate-500">
                  Intelligent ticket management and support automation
                </p>
              </div>
            </div>

            {/* Quick Links with Verified Routes */}
            <nav
              aria-label="Footer Navigation"
              className="flex flex-wrap items-center justify-center gap-5 text-xs font-semibold text-slate-600"
            >
              <Link
                to="/dashboard"
                className="hover:text-indigo-600 transition-colors"
              >
                Dashboard
              </Link>
              <Link
                to="/tickets"
                className="hover:text-indigo-600 transition-colors"
              >
                Tickets
              </Link>
              <Link
                to="/tickets/new"
                className="hover:text-indigo-600 transition-colors"
              >
                Submit Ticket
              </Link>
              <Link
                to="/notifications"
                className="hover:text-indigo-600 transition-colors"
              >
                Notifications
              </Link>
            </nav>
          </div>

          {/* Copyright & Compliance */}
          <div className="pt-6 text-center sm:text-left text-xs text-slate-400 flex flex-col sm:flex-row items-center justify-between gap-2">
            <p>
              &copy; {new Date().getFullYear()} AI Support Platform. All rights
              reserved.
            </p>
            <p className="text-[11px] text-slate-400">
              Authenticated Session &bull; Customer Portal
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
