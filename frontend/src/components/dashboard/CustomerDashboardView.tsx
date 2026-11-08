import { useState, useEffect, useCallback } from 'react';
import { Link, useLocation } from 'react-router-dom';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import {
  LayoutGrid,
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
  Zap,
  CheckCircle2,
  FileText,
  Search,
  Users,
  Shield,
  Bot,
  MessageSquare,
  User,
  UserCheck,
} from 'lucide-react';
import { BrandLogo } from '@/components/common/BrandLogo';
import { StatusBadge } from '@/components/badges/StatusBadge';
import { PriorityBadge } from '@/components/badges/PriorityBadge';
import { CategoryBadge } from '@/components/badges/CategoryBadge';
import { NotificationBell } from '@/components/notifications/NotificationBell';
import { getTicketsApi, getTicketStatsApi } from '@/lib/api/tickets';
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

    const statsPromise = getTicketStatsApi()
      .then((statsRes) => {
        setStats(statsRes);
      })
      .catch(() => {
        setStatsError(true);
      })
      .finally(() => {
        setIsLoadingStats(false);
      });

    const ticketsPromise = getTicketsApi({
      page: 0,
      size: 5,
      sortBy: 'createdAt',
      direction: 'desc',
    })
      .then((allTicketsRes) => {
        setRecentTickets(allTicketsRes.content);
      })
      .catch(() => {
        setTicketsError(true);
      })
      .finally(() => {
        setIsLoadingTickets(false);
      });

    await Promise.allSettled([statsPromise, ticketsPromise]);
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
  const ticketsLabel =
    user.role === 'AGENT'
      ? 'Assigned Tickets'
      : user.role === 'ADMIN'
      ? 'All Tickets'
      : 'My Tickets';

  return (
    <div className="min-h-screen bg-slate-50/60 flex flex-col font-sans text-slate-800 antialiased selection:bg-indigo-100 selection:text-indigo-900">
      {/* ========================================================================= */}
      {/* 1. TOP NAVBAR                                                             */}
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
              <span className="text-base sm:text-lg font-bold text-slate-900 tracking-tight">
                AI Support Platform
              </span>
            </Link>
          </div>

          {/* Center: Rounded Navigation Pills */}
          <nav
            aria-label="Main Navigation"
            className="hidden md:flex items-center gap-2.5"
          >
            {/* Dashboard Pill (Active) */}
            <Link
              to="/dashboard"
              className={`inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs sm:text-sm font-semibold transition-all ${
                location.pathname === '/dashboard'
                  ? 'bg-blue-100/80 text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80'
              }`}
            >
              <LayoutGrid className="w-4 h-4 text-blue-600" />
              <span>Dashboard</span>
            </Link>

            {/* Tickets Pill */}
            <Link
              to="/tickets"
              className={`inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs sm:text-sm font-semibold transition-all ${
                location.pathname === '/tickets'
                  ? 'bg-blue-100/80 text-blue-700 shadow-xs'
                  : 'text-purple-700 hover:text-purple-900 hover:bg-purple-50/60'
              }`}
            >
              <Ticket className="w-4 h-4 text-purple-600" />
              <span>{ticketsLabel}</span>
            </Link>

            {/* Submit Ticket Pill (Visible for CUSTOMER & AGENT, hidden for ADMIN) */}
            {user.role !== 'ADMIN' && (
              <Link
                to="/tickets/new"
                className={`inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs sm:text-sm font-semibold transition-all ${
                  location.pathname === '/tickets/new'
                    ? 'bg-blue-100/80 text-blue-700 shadow-xs'
                    : 'text-purple-700 hover:text-purple-900 hover:bg-purple-50/60'
                }`}
              >
                <PlusCircle className="w-4 h-4 text-purple-600" />
                <span>Submit Ticket</span>
              </Link>
            )}

            {/* Agent Requests Pill (Visible for ADMIN only) */}
            {user.role === 'ADMIN' && (
              <Link
                to="/admin/agent-requests"
                className={`inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs sm:text-sm font-semibold transition-all ${
                  location.pathname === '/admin/agent-requests'
                    ? 'bg-blue-100/80 text-blue-700 shadow-xs'
                    : 'text-purple-700 hover:text-purple-900 hover:bg-purple-50/60'
                }`}
              >
                <UserCheck className="w-4 h-4 text-purple-600" />
                <span>Agent Requests</span>
              </Link>
            )}
          </nav>

          {/* Right: Notification Bell & Profile Dropdown */}
          <div className="flex items-center gap-2 sm:gap-3">
            {/* Reused NotificationBell component */}
            <NotificationBell />

            {/* Profile Dropdown */}
            <DropdownMenu.Root>
              <DropdownMenu.Trigger asChild>
                <button
                  type="button"
                  aria-label="Open user menu"
                  className="flex items-center gap-2 p-1 rounded-full hover:bg-slate-100 text-slate-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                >
                  <div className="w-8 h-8 rounded-full bg-purple-600 text-white font-bold text-xs flex items-center justify-center shadow-xs">
                    {userInitial}
                  </div>
                  <span className="hidden sm:inline text-sm font-semibold text-slate-800 max-w-[130px] truncate">
                    {displayName}
                  </span>
                  <ChevronDown className="hidden sm:inline w-3.5 h-3.5 text-slate-500" />
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
                      to="/profile"
                      className="flex items-center gap-2 px-3 py-2 text-xs font-medium text-slate-700 rounded-lg hover:bg-slate-100 hover:text-slate-900 cursor-pointer outline-none transition-colors"
                    >
                      <User className="w-3.5 h-3.5 text-slate-400" />
                      <span>Profile</span>
                    </Link>
                  </DropdownMenu.Item>

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

        {/* Mobile Navigation Drawer */}
        {mobileNavOpen && (
          <div
            id="mobile-nav-panel"
            className="md:hidden border-t border-slate-200/80 bg-white/98 px-4 pt-3 pb-5 shadow-lg space-y-3 animate-in slide-in-from-top-2 duration-150"
          >
            <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
              <div className="w-9 h-9 rounded-full bg-purple-600 text-white font-bold text-sm flex items-center justify-center shadow-xs">
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
                className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium ${
                  location.pathname === '/dashboard'
                    ? 'bg-blue-50 text-blue-700 font-semibold'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                <LayoutGrid className="w-4 h-4 text-blue-600" />
                <span>Dashboard</span>
              </Link>
              <Link
                to="/tickets"
                onClick={() => setMobileNavOpen(false)}
                className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium ${
                  location.pathname === '/tickets'
                    ? 'bg-purple-50 text-purple-700 font-semibold'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                <Ticket className="w-4 h-4 text-purple-600" />
                <span>{ticketsLabel}</span>
              </Link>
              {user.role !== 'ADMIN' && (
                <Link
                  to="/tickets/new"
                  onClick={() => setMobileNavOpen(false)}
                  className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-purple-700 hover:bg-purple-50"
                >
                  <PlusCircle className="w-4 h-4 text-purple-600" />
                  <span>Submit Ticket</span>
                </Link>
              )}
              {user.role === 'ADMIN' && (
                <Link
                  to="/admin/agent-requests"
                  onClick={() => setMobileNavOpen(false)}
                  className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-purple-700 hover:bg-purple-50"
                >
                  <UserCheck className="w-4 h-4 text-purple-600" />
                  <span>Agent Requests</span>
                </Link>
              )}
              <div className="pt-2 border-t border-slate-100" />
              <Link
                to="/profile"
                onClick={() => setMobileNavOpen(false)}
                className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                <User className="w-4 h-4 text-slate-400" />
                <span>Profile</span>
              </Link>
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
        {/* ======================================================================= */}
        {/* A. HERO SECTION WITH ROBOT ILLUSTRATION & FLOATING BADGES               */}
        {/* ======================================================================= */}
        <section
          aria-labelledby="welcome-heading"
          className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-50/90 via-indigo-50/70 to-purple-50/80 border border-indigo-100/60 p-6 sm:p-10 lg:p-12 mb-8 shadow-xs"
        >
          {/* Subtle Ambient Sparkle Stars in Background */}
          <div
            className="absolute top-6 left-1/3 text-indigo-300 opacity-60 pointer-events-none"
            aria-hidden="true"
          >
            <Sparkles className="w-3.5 h-3.5" />
          </div>
          <div
            className="absolute bottom-8 left-1/2 text-purple-300 opacity-50 pointer-events-none"
            aria-hidden="true"
          >
            <Sparkles className="w-4 h-4" />
          </div>
          <div
            className="absolute top-12 right-1/4 text-blue-300 opacity-50 pointer-events-none"
            aria-hidden="true"
          >
            <Sparkles className="w-3.5 h-3.5" />
          </div>

          <div className="relative z-10 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            {/* Left Column: Heading, Subtitle, Description */}
            <div className="lg:col-span-7 space-y-3">
              <h1
                id="welcome-heading"
                className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-slate-900 tracking-tight leading-tight"
              >
                Welcome back,
                <br />
                <span className="text-blue-600 font-extrabold">
                  {displayName}!
                </span>{' '}
                <span role="img" aria-label="Waving hand">
                  👋
                </span>
              </h1>
              <p className="text-base sm:text-lg font-bold text-slate-700 pt-1">
                AI-Powered Support Ticket Management Platform
              </p>
              <p className="text-sm sm:text-base text-slate-600 max-w-xl leading-relaxed">
                Create, track, and resolve your support requests with the help of
                AI. Get faster, smarter, and more efficient support.
              </p>
            </div>

            {/* Right Column: AI Robot Vector Composition with 3 Floating Badges */}
            <div className="lg:col-span-5 relative flex items-center justify-center min-h-[220px] sm:min-h-[260px]">
              {/* Floating Badge 1: Top-Left (AI Classifies Your Ticket) */}
              <div className="absolute top-0 left-2 sm:left-4 z-20 flex items-center gap-2 px-3 py-1.5 sm:px-3.5 sm:py-2 rounded-2xl bg-white shadow-md border border-slate-100 animate-in fade-in zoom-in duration-200">
                <div className="w-6 h-6 rounded-lg bg-purple-100 flex items-center justify-center text-purple-600 shrink-0">
                  <Sparkles className="w-3.5 h-3.5" />
                </div>
                <div className="text-left">
                  <p className="text-[10px] sm:text-[11px] font-bold text-slate-900 leading-tight">
                    AI Classifies
                  </p>
                  <p className="text-[9px] sm:text-[10px] text-slate-500 leading-tight">
                    Your Ticket
                  </p>
                </div>
              </div>

              {/* Floating Badge 2: Top-Right (Faster Resolutions) */}
              <div className="absolute top-2 right-2 sm:right-4 z-20 flex items-center gap-2 px-3 py-1.5 sm:px-3.5 sm:py-2 rounded-2xl bg-white shadow-md border border-slate-100 animate-in fade-in zoom-in duration-200 delay-100">
                <div className="w-6 h-6 rounded-lg bg-sky-100 flex items-center justify-center text-sky-600 shrink-0">
                  <Zap className="w-3.5 h-3.5" />
                </div>
                <div className="text-left">
                  <p className="text-[10px] sm:text-[11px] font-bold text-slate-900 leading-tight">
                    Faster
                  </p>
                  <p className="text-[9px] sm:text-[10px] text-slate-500 leading-tight">
                    Resolutions
                  </p>
                </div>
              </div>

              {/* Center: Friendly AI Robot Illustration with Laptop */}
              <div className="relative z-10 w-44 sm:w-56 h-44 sm:h-56 flex items-center justify-center">
                <svg
                  viewBox="0 0 240 220"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  className="w-full h-full drop-shadow-lg"
                  aria-hidden="true"
                >
                  <defs>
                    <radialGradient id="botBackglow" cx="50%" cy="46%" r="50%">
                      <stop offset="0%" stopColor="#60A5FA" stopOpacity="0.35" />
                      <stop offset="65%" stopColor="#818CF8" stopOpacity="0.18" />
                      <stop offset="100%" stopColor="#C084FC" stopOpacity="0" />
                    </radialGradient>
                    <radialGradient id="botShadow" cx="50%" cy="50%" r="50%">
                      <stop offset="0%" stopColor="#1E293B" stopOpacity="0.28" />
                      <stop offset="100%" stopColor="#1E293B" stopOpacity="0" />
                    </radialGradient>
                    <linearGradient id="botHeadGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#FFFFFF" />
                      <stop offset="70%" stopColor="#F1F5F9" />
                      <stop offset="100%" stopColor="#E2E8F0" />
                    </linearGradient>
                    <linearGradient id="botEarGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" stopColor="#38BDF8" />
                      <stop offset="100%" stopColor="#2563EB" />
                    </linearGradient>
                    <linearGradient id="botVisorGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" stopColor="#0B1120" />
                      <stop offset="50%" stopColor="#0F172A" />
                      <stop offset="100%" stopColor="#1E293B" />
                    </linearGradient>
                    <linearGradient id="botEyeGlow" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#67E8F9" />
                      <stop offset="100%" stopColor="#38BDF8" />
                    </linearGradient>
                    <linearGradient id="botBodyGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#FFFFFF" />
                      <stop offset="60%" stopColor="#F8FAFC" />
                      <stop offset="100%" stopColor="#E2E8F0" />
                    </linearGradient>
                    <linearGradient id="laptopLidGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#3B82F6" />
                      <stop offset="50%" stopColor="#2563EB" />
                      <stop offset="100%" stopColor="#1D4ED8" />
                    </linearGradient>
                    <linearGradient id="laptopScreenGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" stopColor="#0F172A" />
                      <stop offset="100%" stopColor="#1E293B" />
                    </linearGradient>
                    <linearGradient id="laptopBaseGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" stopColor="#94A3B8" />
                      <stop offset="50%" stopColor="#CBD5E1" />
                      <stop offset="100%" stopColor="#94A3B8" />
                    </linearGradient>
                  </defs>

                  {/* Ground Contact Shadow */}
                  <ellipse cx="120" cy="204" rx="76" ry="7" fill="url(#botShadow)" />

                  {/* Ambient Soft Backglow */}
                  <circle cx="120" cy="98" r="88" fill="url(#botBackglow)" />

                  {/* Antenna stem and glowing orb */}
                  <line
                    x1="120"
                    y1="36"
                    x2="120"
                    y2="18"
                    stroke="#38BDF8"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <circle cx="120" cy="14" r="6" fill="#38BDF8" />
                  <circle cx="120" cy="14" r="9" stroke="#67E8F9" strokeWidth="1.5" strokeOpacity="0.5" />
                  <circle cx="118.5" cy="12.5" r="2" fill="#FFFFFF" />

                  {/* Head Ear Pods (Left & Right) */}
                  <rect x="46" y="62" width="14" height="34" rx="7" fill="url(#botEarGrad)" />
                  <rect x="48" y="68" width="10" height="22" rx="5" fill="#0284C7" />
                  <rect x="180" y="62" width="14" height="34" rx="7" fill="url(#botEarGrad)" />
                  <rect x="182" y="68" width="10" height="22" rx="5" fill="#0284C7" />

                  {/* Head Chassis */}
                  <rect
                    x="56"
                    y="32"
                    width="128"
                    height="92"
                    rx="32"
                    fill="url(#botHeadGrad)"
                    stroke="#CBD5E1"
                    strokeWidth="2"
                  />

                  {/* Head Gloss / Highlight strip */}
                  <path
                    d="M 74 36 Q 120 40 166 36"
                    stroke="#FFFFFF"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeOpacity="0.8"
                  />

                  {/* Face Visor Screen */}
                  <rect
                    x="68"
                    y="44"
                    width="104"
                    height="68"
                    rx="24"
                    fill="url(#botVisorGrad)"
                    stroke="#334155"
                    strokeWidth="1.5"
                  />

                  {/* Visor Glare Curve */}
                  <path
                    d="M 78 50 Q 120 54 158 58"
                    stroke="#FFFFFF"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                    strokeOpacity="0.25"
                  />

                  {/* Left Friendly Glowing Eye */}
                  <g>
                    <rect x="86" y="62" width="20" height="24" rx="10" fill="url(#botEyeGlow)" />
                    {/* Eye Sparkle Highlights */}
                    <circle cx="94" cy="69" r="3.5" fill="#FFFFFF" />
                    <circle cx="99" cy="77" r="1.5" fill="#FFFFFF" />
                  </g>

                  {/* Right Friendly Glowing Eye */}
                  <g>
                    <rect x="134" y="62" width="20" height="24" rx="10" fill="url(#botEyeGlow)" />
                    {/* Eye Sparkle Highlights */}
                    <circle cx="142" cy="69" r="3.5" fill="#FFFFFF" />
                    <circle cx="147" cy="77" r="1.5" fill="#FFFFFF" />
                  </g>

                  {/* Cheerful Rosy Cheeks */}
                  <ellipse cx="80" cy="88" rx="5" ry="2.5" fill="#38BDF8" fillOpacity="0.3" />
                  <ellipse cx="160" cy="88" rx="5" ry="2.5" fill="#38BDF8" fillOpacity="0.3" />

                  {/* Warm Curved Smile */}
                  <path
                    d="M 112 91 Q 120 100 128 91"
                    stroke="#38BDF8"
                    strokeWidth="3.2"
                    strokeLinecap="round"
                    fill="none"
                  />

                  {/* Neck Joint */}
                  <rect x="108" y="122" width="24" height="10" rx="3" fill="#64748B" />
                  <rect x="112" y="124" width="16" height="3" rx="1.5" fill="#38BDF8" />

                  {/* Torso Chassis */}
                  <rect
                    x="76"
                    y="130"
                    width="88"
                    height="54"
                    rx="22"
                    fill="url(#botBodyGrad)"
                    stroke="#CBD5E1"
                    strokeWidth="1.5"
                  />

                  {/* Chest AI Core Emblem */}
                  <circle cx="120" cy="148" r="9" fill="#0F172A" />
                  <circle cx="120" cy="148" r="6" fill="#38BDF8" />
                  <circle cx="120" cy="148" r="2.5" fill="#FFFFFF" />

                  {/* Left & Right Shoulders / Upper Arms */}
                  <path
                    d="M 76 138 C 65 146 62 165 72 176"
                    stroke="#CBD5E1"
                    strokeWidth="9"
                    strokeLinecap="round"
                  />
                  <path
                    d="M 164 138 C 175 146 178 165 168 176"
                    stroke="#CBD5E1"
                    strokeWidth="9"
                    strokeLinecap="round"
                  />

                  {/* Modern Laptop - Open Screen & Glow */}
                  <path
                    d="M 68 156 L 172 156 L 164 192 L 76 192 Z"
                    fill="url(#laptopLidGrad)"
                    stroke="#1E40AF"
                    strokeWidth="1.2"
                  />
                  <rect
                    x="78"
                    y="160"
                    width="84"
                    height="28"
                    rx="3"
                    fill="url(#laptopScreenGrad)"
                  />
                  {/* Laptop Screen Content - glowing lines & AI pulse */}
                  <line x1="84" y1="168" x2="114" y2="168" stroke="#38BDF8" strokeWidth="2" strokeLinecap="round" />
                  <line x1="84" y1="174" x2="104" y2="174" stroke="#818CF8" strokeWidth="2" strokeLinecap="round" />
                  <circle cx="146" cy="172" r="5" fill="#38BDF8" />
                  <circle cx="146" cy="172" r="2" fill="#FFFFFF" />

                  {/* Laptop Base Deck */}
                  <path
                    d="M 58 193 L 182 193 L 176 202 L 64 202 Z"
                    fill="url(#laptopBaseGrad)"
                    stroke="#64748B"
                    strokeWidth="1"
                  />
                  {/* Trackpad */}
                  <rect x="108" y="195" width="24" height="5" rx="1.5" fill="#94A3B8" />

                  {/* Friendly Hands Typing on Deck */}
                  <ellipse cx="80" cy="191" rx="8" ry="6" fill="#F8FAFC" stroke="#94A3B8" strokeWidth="1.2" />
                  <ellipse cx="160" cy="191" rx="8" ry="6" fill="#F8FAFC" stroke="#94A3B8" strokeWidth="1.2" />
                </svg>
              </div>

              {/* Floating Badge 3: Bottom-Right (Your Support, Simplified) */}
              <div className="absolute bottom-1 right-1 sm:right-3 z-20 flex items-center gap-2 px-3 py-1.5 sm:px-3.5 sm:py-2 rounded-2xl bg-white shadow-md border border-slate-100 animate-in fade-in zoom-in duration-200 delay-200">
                <div className="w-6 h-6 rounded-lg bg-purple-100 flex items-center justify-center text-purple-600 shrink-0">
                  <MessageSquare className="w-3.5 h-3.5" />
                </div>
                <div className="text-left">
                  <p className="text-[10px] sm:text-[11px] font-bold text-slate-900 leading-tight">
                    Your Support,
                  </p>
                  <p className="text-[9px] sm:text-[10px] text-slate-500 leading-tight">
                    Simplified
                  </p>
                </div>
              </div>
            </div>
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

        {/* ======================================================================= */}
        {/* B. FOUR TICKET STATISTICS CARDS (EXACT PASTEL COLOR PALETTE)            */}
        {/* ======================================================================= */}
        <section
          aria-label="Ticket Overview Statistics"
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-8"
        >
          {/* Card 1: TOTAL TICKETS (Pastel Lavender) */}
          <div className="rounded-2xl bg-[#faf5ff] border border-purple-100/90 p-6 shadow-xs transition-shadow hover:shadow-md">
            <div className="w-12 h-12 rounded-xl bg-[#f3e8ff] text-purple-600 flex items-center justify-center mb-4">
              <Ticket className="w-6 h-6" />
            </div>
            <span className="text-xs font-bold uppercase tracking-wider text-purple-700">
              Total Tickets
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-purple-200/60 rounded animate-pulse" />
              ) : statsError ? (
                <span
                  className="text-2xl font-bold text-slate-400"
                  title="Data unavailable"
                >
                  —
                </span>
              ) : (
                <span className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.total ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              All support tickets created
            </p>
          </div>

          {/* Card 2: OPEN TICKETS (Pastel Warm Amber/Peach) */}
          <div className="rounded-2xl bg-[#fffbeb] border border-amber-100/90 p-6 shadow-xs transition-shadow hover:shadow-md">
            <div className="w-12 h-12 rounded-xl bg-[#fef3c7] text-amber-600 flex items-center justify-center mb-4">
              <Clock className="w-6 h-6" />
            </div>
            <span className="text-xs font-bold uppercase tracking-wider text-amber-700">
              Open Tickets
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-amber-200/60 rounded animate-pulse" />
              ) : statsError ? (
                <span
                  className="text-2xl font-bold text-slate-400"
                  title="Data unavailable"
                >
                  —
                </span>
              ) : (
                <span className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.open ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Awaiting agent review
            </p>
          </div>

          {/* Card 3: IN PROGRESS (Pastel Sky Blue) */}
          <div className="rounded-2xl bg-[#f0f9ff] border border-sky-100/90 p-6 shadow-xs transition-shadow hover:shadow-md">
            <div className="w-12 h-12 rounded-xl bg-[#e0f2fe] text-sky-600 flex items-center justify-center mb-4">
              <RefreshCw className="w-6 h-6" />
            </div>
            <span className="text-xs font-bold uppercase tracking-wider text-sky-700">
              In Progress
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-sky-200/60 rounded animate-pulse" />
              ) : statsError ? (
                <span
                  className="text-2xl font-bold text-slate-400"
                  title="Data unavailable"
                >
                  —
                </span>
              ) : (
                <span className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.inProgress ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Currently being investigated
            </p>
          </div>

          {/* Card 4: RESOLVED (Pastel Mint Green) */}
          <div className="rounded-2xl bg-[#f0fdf4] border border-emerald-100/90 p-6 shadow-xs transition-shadow hover:shadow-md">
            <div className="w-12 h-12 rounded-xl bg-[#dcfce7] text-emerald-600 flex items-center justify-center mb-4">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <span className="text-xs font-bold uppercase tracking-wider text-emerald-700">
              Resolved
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              {isLoadingStats ? (
                <div className="h-9 w-16 bg-emerald-200/60 rounded animate-pulse" />
              ) : statsError ? (
                <span
                  className="text-2xl font-bold text-slate-400"
                  title="Data unavailable"
                >
                  —
                </span>
              ) : (
                <span className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
                  {stats?.resolved ?? 0}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Successfully completed
            </p>
          </div>
        </section>

        {/* ======================================================================= */}
        {/* C. TWO ILLUSTRATED QUICK-ACTION CARDS                                   */}
        {/* ======================================================================= */}
        <section
          aria-label="Quick Actions"
          className={`grid ${
            user.role === 'ADMIN' ? 'grid-cols-1' : 'grid-cols-1 md:grid-cols-2'
          } gap-5 mb-8`}
        >
          {/* Action Card 1: View Support Tickets */}
          <div className="rounded-2xl bg-white border border-purple-100/90 p-6 shadow-xs hover:shadow-md transition-all flex flex-col justify-between">
            <div className="flex items-start gap-4">
              {/* Document + Magnifying Glass Icon Box */}
              <div className="w-14 h-14 rounded-2xl bg-purple-100/70 text-purple-600 flex items-center justify-center shrink-0">
                <div className="relative">
                  <FileText className="w-7 h-7" />
                  <Search className="w-4 h-4 text-purple-700 absolute -bottom-1 -right-1 bg-purple-100 rounded-full" />
                </div>
              </div>
              <div>
                <h2 className="text-lg font-bold text-slate-900">
                  {user.role === 'AGENT'
                    ? 'View Assigned Tickets'
                    : user.role === 'ADMIN'
                    ? 'View All Tickets'
                    : 'View My Support Tickets'}
                </h2>
                <p className="mt-1 text-sm text-slate-600 leading-relaxed">
                  {user.role === 'AGENT'
                    ? 'Manage, track, and resolve tickets currently assigned to you.'
                    : user.role === 'ADMIN'
                    ? 'Monitor, filter, and inspect all support tickets across the platform.'
                    : 'Search and filter your submitted tickets by status or priority.'}
                </p>
              </div>
            </div>
            <div className="mt-6">
              <Link
                to="/tickets"
                className="w-full inline-flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-semibold bg-[#f5f0ff] hover:bg-[#ede5ff] text-purple-700 border border-purple-200/80 transition-colors"
              >
                <ArrowRight className="w-4 h-4 rotate-45" />
                <span>
                  {user.role === 'AGENT'
                    ? 'View Assigned Tickets'
                    : user.role === 'ADMIN'
                    ? 'View All Tickets'
                    : 'View Tickets'}
                </span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>

          {/* Action Card 2: Submit a New Ticket (Visible for CUSTOMER and AGENT, hidden for ADMIN) */}
          {user.role !== 'ADMIN' && (
            <div className="rounded-2xl bg-white border border-blue-100/90 p-6 shadow-xs hover:shadow-md transition-all flex flex-col justify-between">
              <div className="flex items-start gap-4">
                {/* Document + Plus Badge Icon Box */}
                <div className="w-14 h-14 rounded-2xl bg-blue-100/70 text-blue-600 flex items-center justify-center shrink-0">
                  <div className="relative">
                    <FileText className="w-7 h-7" />
                    <PlusCircle className="w-4 h-4 text-blue-700 absolute -bottom-1 -right-1 bg-white rounded-full" />
                  </div>
                </div>
                <div>
                  <h2 className="text-lg font-bold text-slate-900">
                    Submit a New Ticket
                  </h2>
                  <p className="mt-1 text-sm text-slate-600 leading-relaxed">
                    Create a support ticket for an issue or request.
                  </p>
                </div>
              </div>
              <div className="mt-6">
                <Link
                  to="/tickets/new"
                  className="w-full inline-flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-semibold bg-blue-600 hover:bg-blue-700 text-white shadow-sm transition-colors"
                >
                  <PlusCircle className="w-4 h-4" />
                  <span>Create Ticket</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
              </div>
            </div>
          )}
        </section>

        {/* ======================================================================= */}
        {/* D. RECENT TICKETS SECTION                                               */}
        {/* ======================================================================= */}
        <section
          aria-labelledby="recent-tickets-heading"
          className="rounded-2xl bg-white border border-slate-200/90 shadow-xs overflow-hidden mb-10"
        >
          {/* Header Bar */}
          <div className="p-5 sm:px-6 flex items-center justify-between border-b border-slate-100">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                <FileText className="w-5 h-5" />
              </div>
              <div>
                <h2
                  id="recent-tickets-heading"
                  className="text-lg font-bold text-slate-900"
                >
                  Recent Tickets
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Your latest support inquiries and real-time statuses
                </p>
              </div>
            </div>
            <Link
              to="/tickets"
              className="inline-flex items-center gap-1 text-sm font-semibold text-blue-600 hover:text-blue-800 transition-colors"
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
                className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors shadow-xs"
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
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold bg-blue-600 text-white hover:bg-blue-700 transition-colors shadow-xs"
              >
                <PlusCircle className="w-4 h-4" />
                <span>Create Your First Ticket</span>
              </Link>
            </div>
          )}

          {/* Populated Table for Desktop and Tablet (>=768px) */}
          {!isLoadingTickets && !ticketsError && recentTickets.length > 0 && (
            <div className="hidden md:block overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="bg-slate-50/70 text-[11px] font-bold uppercase tracking-wider text-slate-500 border-b border-slate-200/80">
                  <tr>
                    <th scope="col" className="py-3.5 px-6">
                      Ticket ID
                    </th>
                    <th scope="col" className="py-3.5 px-6">
                      Title
                    </th>
                    <th scope="col" className="py-3.5 px-6">
                      Status
                    </th>
                    <th scope="col" className="py-3.5 px-6">
                      Priority
                    </th>
                    <th scope="col" className="py-3.5 px-6">
                      Category
                    </th>
                    <th scope="col" className="py-3.5 px-6">
                      Created
                    </th>
                    <th scope="col" className="py-3.5 px-6 text-right">
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
                      <td className="py-4 px-6 font-mono text-xs font-bold text-slate-900">
                        #{ticket.id}
                      </td>
                      <td className="py-4 px-6">
                        <Link
                          to={`/tickets/${ticket.id}`}
                          className="font-semibold text-slate-900 group-hover:text-blue-600 transition-colors line-clamp-1"
                        >
                          {ticket.title}
                        </Link>
                      </td>
                      <td className="py-4 px-6">
                        <StatusBadge status={ticket.status} />
                      </td>
                      <td className="py-4 px-6">
                        <PriorityBadge priority={ticket.priority} />
                      </td>
                      <td className="py-4 px-6">
                        <CategoryBadge category={ticket.category} />
                      </td>
                      <td className="py-4 px-6 text-xs text-slate-500 whitespace-nowrap">
                        {formatTimeAgo(ticket.createdAt)}
                      </td>
                      <td className="py-4 px-6 text-right">
                        <Link
                          to={`/tickets/${ticket.id}`}
                          className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-800 transition-colors"
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

          {/* Stacked Cards for Mobile and Narrow Viewports (<768px) */}
          {!isLoadingTickets && !ticketsError && recentTickets.length > 0 && (
            <div className="md:hidden divide-y divide-slate-100">
              {recentTickets.map((ticket) => (
                <div key={ticket.id} className="p-4 space-y-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs font-bold text-slate-900">
                      #{ticket.id}
                    </span>
                    <StatusBadge status={ticket.status} />
                  </div>
                  <Link
                    to={`/tickets/${ticket.id}`}
                    className="block font-semibold text-sm text-slate-900 hover:text-blue-600 transition-colors"
                  >
                    {ticket.title}
                  </Link>
                  <div className="flex items-center gap-2 pt-0.5">
                    <CategoryBadge category={ticket.category} />
                    <PriorityBadge priority={ticket.priority} />
                  </div>
                  <div className="flex items-center justify-between text-xs text-slate-500 pt-2 border-t border-slate-50">
                    <span>{formatTimeAgo(ticket.createdAt)}</span>
                    <Link
                      to={`/tickets/${ticket.id}`}
                      className="inline-flex items-center gap-1 font-semibold text-blue-600"
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

        {/* ======================================================================= */}
        {/* E. "SUPPORT MADE SMARTER WITH AI" SECTION (WORKFLOW & FEATURES)         */}
        {/* ======================================================================= */}
        <section
          aria-labelledby="workflow-heading"
          className="rounded-3xl bg-gradient-to-b from-[#f8f9ff] via-[#f1edff] to-[#f8f9ff] border border-purple-100/60 p-6 sm:p-10 mb-10 shadow-xs"
        >
          {/* Header */}
          <div className="text-left mb-8">
            <span className="inline-block px-3 py-1 rounded-full text-xs font-bold tracking-wider uppercase bg-purple-100 text-purple-700 mb-3">
              How It Works
            </span>
            <h2
              id="workflow-heading"
              className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-slate-900 tracking-tight"
            >
              Support Made Smarter{' '}
              <span className="bg-gradient-to-r from-purple-600 to-indigo-600 bg-clip-text text-transparent">
                with AI
              </span>
            </h2>
            <p className="mt-2 text-sm sm:text-base text-slate-600 max-w-2xl leading-relaxed">
              From submitting a ticket to getting a resolution, our AI-powered
              platform helps you every step of the way.
            </p>
          </div>

          {/* 4-Step Connected Workflow Diagram */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6 items-center mb-10">
            {/* Step 1 */}
            <div className="relative rounded-2xl bg-white p-5 shadow-sm border border-slate-100 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center mb-3">
                <FileText className="w-6 h-6" />
              </div>
              <p className="text-xs sm:text-sm font-bold text-slate-900 leading-tight">
                Submit
                <br />
                Your Issue
              </p>
            </div>

            {/* Step 2 */}
            <div className="relative rounded-2xl bg-white p-5 shadow-sm border border-slate-100 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-xl bg-purple-100 text-purple-600 flex items-center justify-center mb-3">
                <Sparkles className="w-6 h-6" />
              </div>
              <p className="text-xs sm:text-sm font-bold text-slate-900 leading-tight">
                AI Analyzes
                <br />
                and Categorizes
              </p>
            </div>

            {/* Step 3 (Truthful description: Available in the Agent Queue) */}
            <div className="relative rounded-2xl bg-white p-5 shadow-sm border border-slate-100 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center mb-3">
                <Users className="w-6 h-6" />
              </div>
              <p className="text-xs sm:text-sm font-bold text-slate-900 leading-tight">
                Available
                <br />
                in the Agent Queue
              </p>
            </div>

            {/* Step 4 */}
            <div className="relative rounded-2xl bg-white p-5 shadow-sm border border-slate-100 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-3">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <p className="text-xs sm:text-sm font-bold text-slate-900 leading-tight">
                Get
                <br />
                Resolution
              </p>
            </div>
          </div>

          {/* Four Compact Feature Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
            {/* Feature 1 */}
            <div className="rounded-2xl bg-[#fafaff] border border-indigo-100/70 p-5 shadow-xs">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center mb-3.5">
                <Bot className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-slate-900">
                AI-Powered Classification
              </h3>
              <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
                Automatically categorizes your ticket to get it to the right team
                faster.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="rounded-2xl bg-[#f8fbff] border border-blue-100/70 p-5 shadow-xs">
              <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center mb-3.5">
                <Zap className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-slate-900">
                Faster Response Times
              </h3>
              <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
                Reduces manual processing and helps resolve issues quickly.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="rounded-2xl bg-[#f6fdf9] border border-emerald-100/70 p-5 shadow-xs">
              <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-3.5">
                <Users className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-slate-900">
                Better Support Experience
              </h3>
              <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
                Connects you with the right team for accurate and relevant
                solutions.
              </p>
            </div>

            {/* Feature 4 (Truthful description: Role-based authorization & audit tracking) */}
            <div className="rounded-2xl bg-[#fffdf5] border border-amber-100/70 p-5 shadow-xs">
              <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center mb-3.5">
                <Shield className="w-5 h-5" />
              </div>
              <h3 className="text-sm font-bold text-slate-900">
                Secure &amp; Reliable
              </h3>
              <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
                Protected by role-based access controls and audit logging to
                help keep support requests secure.
              </p>
            </div>
          </div>
        </section>
      </main>

      {/* ========================================================================= */}
      {/* 3. AUTHENTICATED FOOTER                                                   */}
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
                className="hover:text-blue-600 transition-colors"
              >
                Dashboard
              </Link>
              <Link
                to="/tickets"
                className="hover:text-blue-600 transition-colors"
              >
                {ticketsLabel}
              </Link>
              {user.role !== 'ADMIN' && (
                <Link
                  to="/tickets/new"
                  className="hover:text-blue-600 transition-colors"
                >
                  Submit Ticket
                </Link>
              )}
              {user.role === 'ADMIN' && (
                <Link
                  to="/admin/agent-requests"
                  className="hover:text-blue-600 transition-colors"
                >
                  Agent Requests
                </Link>
              )}
              <Link
                to="/notifications"
                className="hover:text-blue-600 transition-colors"
              >
                Notifications
              </Link>
            </nav>
          </div>

          {/* Copyright */}
          <div className="pt-6 text-center sm:text-left text-xs text-slate-400">
            <p>
              &copy; {new Date().getFullYear()} AI Support Platform. All rights
              reserved.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
