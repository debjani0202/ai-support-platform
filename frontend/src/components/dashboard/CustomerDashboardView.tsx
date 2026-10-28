import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutGrid,
  Ticket,
  PlusCircle,
  Bell,
  KeyRound,
  User as UserIcon,
  Mail,
  CheckCircle2,
  LogOut,
  ArrowRight,
  X,
  Clock,
  RefreshCw,
  Menu,
  Inbox,
} from 'lucide-react';
import { BrandLogo } from '@/components/common/BrandLogo';
import { StatusBadge } from '@/components/badges/StatusBadge';
import { PriorityBadge } from '@/components/badges/PriorityBadge';
import { CategoryBadge } from '@/components/badges/CategoryBadge';
import { NotificationBell } from '@/components/notifications/NotificationBell';
import { getTicketsApi } from '@/lib/api/tickets';
import {
  getMyNotificationsApi,
  getUnreadCountApi,
  markNotificationAsReadApi,
} from '@/lib/api/notifications';
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
  unreadCount = 0,
  isPreview = false,
  onLogout,
  showToast = true,
  initialStats,
  initialTickets,
  initialNotifications,
}: CustomerDashboardViewProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [toastDismissed, setToastDismissed] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  // Stats state
  const [stats, setStats] = useState<TicketStats>(
    initialStats || { total: 0, open: 0, inProgress: 0, resolved: 0 }
  );

  // Recent data state
  const [recentTickets, setRecentTickets] = useState<TicketResponse[]>(
    initialTickets || []
  );
  const [recentNotifications, setRecentNotifications] = useState<NotificationResponse[]>(
    initialNotifications || []
  );
  const [liveUnreadCount, setLiveUnreadCount] = useState<number>(unreadCount);
  const [isLoadingData, setIsLoadingData] = useState<boolean>(
    !initialTickets && !initialStats && !isPreview
  );

  // Fetch live stats & recent items on mount if in interactive mode
  useEffect(() => {
    if (isPreview) return;

    let isMounted = true;
    async function loadDashboardData() {
      try {
        const [
          allTicketsRes,
          openRes,
          inProgressRes,
          resolvedRes,
          notifsRes,
          unreadRes,
        ] = await Promise.all([
          getTicketsApi({ page: 0, size: 5, sortBy: 'createdAt', direction: 'desc' }).catch(
            () => null
          ),
          getTicketsApi({ status: 'OPEN', size: 1 }).catch(() => null),
          getTicketsApi({ status: 'IN_PROGRESS', size: 1 }).catch(() => null),
          getTicketsApi({ status: 'RESOLVED', size: 1 }).catch(() => null),
          getMyNotificationsApi({ page: 0, size: 5 }).catch(() => null),
          getUnreadCountApi().catch(() => null),
        ]);

        if (!isMounted) return;

        if (allTicketsRes) {
          setRecentTickets(allTicketsRes.content);
          setStats((prev) => ({ ...prev, total: allTicketsRes.totalElements }));
        }
        if (openRes) {
          setStats((prev) => ({ ...prev, open: openRes.totalElements }));
        }
        if (inProgressRes) {
          setStats((prev) => ({ ...prev, inProgress: inProgressRes.totalElements }));
        }
        if (resolvedRes) {
          setStats((prev) => ({ ...prev, resolved: resolvedRes.totalElements }));
        }
        if (notifsRes) {
          setRecentNotifications(notifsRes.content);
        }
        if (unreadRes) {
          setLiveUnreadCount(unreadRes.unreadCount);
        }
      } finally {
        if (isMounted) {
          setIsLoadingData(false);
        }
      }
    }

    loadDashboardData();
    return () => {
      isMounted = false;
    };
  }, [isPreview]);

  // Safe time formatting helper
  const formatTimeAgo = (dateStr: string) => {
    try {
      return formatDistanceToNow(parseISO(dateStr), { addSuffix: true });
    } catch {
      return dateStr;
    }
  };

  // Helper to handle notification item clicks
  const handleNotificationClick = (notif: NotificationResponse) => {
    if (isPreview) return;

    if (!notif.read) {
      markNotificationAsReadApi(notif.id).catch(() => {});
      setLiveUnreadCount((prev) => Math.max(0, prev - 1));
      setRecentNotifications((prev) =>
        prev.map((n) => (n.id === notif.id ? { ...n, read: true } : n))
      );
    }

    if (notif.ticketId) {
      navigate(`/tickets/${notif.ticketId}`);
    } else {
      navigate('/notifications');
    }
  };

  // Helper to render either an interactive Link or static div
  const renderLink = (to: string, children: React.ReactNode, className: string) => {
    if (isPreview) {
      return <div className={className}>{children}</div>;
    }
    return (
      <Link to={to} className={className}>
        {children}
      </Link>
    );
  };

  const navLinks = [
    { label: 'Dashboard', path: '/dashboard', icon: LayoutGrid },
    { label: 'Tickets', path: '/tickets', icon: Ticket },
    { label: 'Submit Ticket', path: '/tickets/new', icon: PlusCircle },
    {
      label: 'Notifications',
      path: '/notifications',
      icon: Bell,
      badge: liveUnreadCount > 0 ? liveUnreadCount : null,
    },
    { label: 'Change Password', path: '/change-password', icon: KeyRound },
  ];

  return (
    <div className="min-h-screen bg-[#fafbfc] flex text-slate-800 antialiased">
      {/* 1. LEFT SIDEBAR (Desktop) */}
      <aside className="hidden lg:flex w-64 flex-col border-r border-slate-200/80 bg-white min-h-screen sticky top-0 h-screen shrink-0 select-none">
        {/* Brand Header */}
        <div className="h-16 border-b border-slate-100 flex items-center px-6 gap-3">
          <BrandLogo size={26} />
          <div className="flex flex-col">
            <span className="font-bold text-slate-900 tracking-tight text-sm">
              AI Support Platform
            </span>
            <span className="text-[10px] text-slate-400 font-medium">Customer Portal</span>
          </div>
        </div>

        {/* Navigation Section */}
        <div className="flex-1 py-5 px-4 space-y-1 overflow-y-auto">
          <p className="px-3 pb-2 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
            Navigation
          </p>
          {navLinks.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return isPreview ? (
              <div
                key={item.label}
                className={`flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <Icon className="w-4 h-4 text-blue-600" />
                  <span>{item.label}</span>
                </div>
                {item.badge && (
                  <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-emerald-500 text-white">
                    {item.badge}
                  </span>
                )}
              </div>
            ) : (
              <Link
                key={item.label}
                to={item.path}
                className={`flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <Icon className="w-4 h-4 text-blue-600" />
                  <span>{item.label}</span>
                </div>
                {item.badge && (
                  <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-emerald-500 text-white">
                    {item.badge}
                  </span>
                )}
              </Link>
            );
          })}

          {/* Quick Create Ticket Action in Sidebar */}
          <div className="pt-6 px-1">
            {renderLink(
              '/tickets/new',
              <>
                <PlusCircle className="w-4 h-4" />
                <span>Create New Ticket</span>
              </>,
              'w-full py-2.5 px-3 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-xs font-semibold shadow-xs shadow-blue-500/25 flex items-center justify-center gap-2 transition-all cursor-pointer'
            )}
          </div>
        </div>

        {/* Sidebar Footer User Card */}
        <div className="p-4 border-t border-slate-100 bg-slate-50/50">
          <div className="p-3 rounded-xl bg-white border border-slate-200/80 shadow-xs flex items-center justify-between">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-600 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-xs">
                {(user.name || 'C').charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900 truncate">{user.name}</p>
                <span className="inline-flex items-center gap-1 text-[9px] font-semibold text-emerald-700">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                  {user.role}
                </span>
              </div>
            </div>
            {!isPreview && onLogout && (
              <button
                type="button"
                onClick={onLogout}
                className="text-slate-400 hover:text-rose-600 p-1.5 rounded-lg hover:bg-rose-50 transition-colors"
                title="Sign Out"
                aria-label="Sign out of account"
              >
                <LogOut className="w-4 h-4" />
              </button>
            )}
          </div>
        </div>
      </aside>

      {/* Mobile Drawer (When Open) */}
      {mobileNavOpen && (
        <div className="fixed inset-0 z-50 lg:hidden flex">
          <div
            className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs"
            onClick={() => setMobileNavOpen(false)}
          />
          <div className="relative w-64 bg-white h-full flex flex-col shadow-xl z-10">
            <div className="h-16 border-b border-slate-100 flex items-center justify-between px-5">
              <div className="flex items-center gap-2">
                <BrandLogo size={24} />
                <span className="font-bold text-slate-900 text-sm">AI Support</span>
              </div>
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                className="p-1 rounded-md text-slate-400 hover:text-slate-700"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
              {navLinks.map((item) => (
                <Link
                  key={item.label}
                  to={item.path}
                  onClick={() => setMobileNavOpen(false)}
                  className="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100"
                >
                  <div className="flex items-center gap-2.5">
                    <item.icon className="w-4 h-4 text-blue-600" />
                    <span>{item.label}</span>
                  </div>
                  {item.badge && (
                    <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-emerald-500 text-white">
                      {item.badge}
                    </span>
                  )}
                </Link>
              ))}
            </div>
            {onLogout && (
              <div className="p-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => {
                    setMobileNavOpen(false);
                    onLogout();
                  }}
                  className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-semibold text-rose-600 bg-rose-50 hover:bg-rose-100 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Sign Out</span>
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 2. MAIN CONTENT AREA */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top Header */}
        <header className="border-b border-slate-100 bg-white/95 px-5 sm:px-8 py-3.5 flex items-center justify-between sticky top-0 z-30 backdrop-blur-xs">
          <div className="flex items-center gap-3">
            {/* Mobile Hamburger Button */}
            <button
              type="button"
              onClick={() => setMobileNavOpen(true)}
              className="lg:hidden p-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition-colors"
              aria-label="Open navigation menu"
            >
              <Menu className="w-5 h-5" />
            </button>

            <div>
              <h2 className="text-sm sm:text-base font-bold text-slate-900 tracking-tight leading-tight">
                Dashboard Overview
              </h2>
              <p className="text-[11px] text-slate-400 font-medium hidden sm:block">
                AI Support Ticket Management Platform
              </p>
            </div>
          </div>

          {/* Right Header Actions */}
          <div className="flex items-center gap-3 sm:gap-4">
            {/* Prominent Create New Ticket Button */}
            {renderLink(
              '/tickets/new',
              <>
                <PlusCircle className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Create New Ticket</span>
                <span className="sm:hidden">New</span>
              </>,
              'px-3 sm:px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-semibold text-xs shadow-xs shadow-blue-500/20 inline-flex items-center gap-1.5 transition-all cursor-pointer'
            )}

            {/* Notification Bell */}
            {isPreview ? (
              <div className="p-1.5 text-slate-500 relative">
                <Bell className="w-4 h-4" />
                {liveUnreadCount > 0 && (
                  <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-emerald-500 ring-2 ring-white" />
                )}
              </div>
            ) : (
              <NotificationBell />
            )}

            {/* User Profile Display */}
            <div className="hidden sm:flex flex-col items-end leading-tight">
              <span className="text-xs font-bold text-slate-900">{user.name}</span>
              <span className="inline-flex items-center gap-1 mt-0.5 px-2 py-0.2 rounded-full text-[9px] font-semibold border border-emerald-300 bg-emerald-50 text-emerald-700">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                {user.role}
              </span>
            </div>

            {/* Sign Out Button */}
            {isPreview ? (
              <div className="flex items-center gap-1 text-xs font-medium text-slate-600 px-2 py-1">
                <LogOut className="w-3.5 h-3.5" />
                <span className="hidden md:inline">Sign Out</span>
              </div>
            ) : (
              onLogout && (
                <button
                  type="button"
                  onClick={onLogout}
                  className="flex items-center gap-1 text-xs font-medium text-slate-600 hover:text-rose-600 hover:bg-rose-50 px-2 py-1 rounded-md transition-colors"
                  title="Sign Out"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span className="hidden md:inline">Sign Out</span>
                </button>
              )
            )}
          </div>
        </header>

        {/* Dashboard Body Content */}
        <main className="flex-1 p-5 sm:p-7 space-y-6 max-w-7xl w-full mx-auto">
          {/* Top Floating Toast Notification */}
          {showToast && !toastDismissed && (
            <div className="flex justify-end">
              <div className="inline-flex items-center gap-2.5 px-3.5 py-2 rounded-xl bg-emerald-50/90 border border-emerald-200/90 text-emerald-900 text-xs shadow-xs transition-all animate-in fade-in slide-in-from-top-1">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                <span className="font-medium">Welcome back, {user.name}!</span>
                {!isPreview && (
                  <button
                    type="button"
                    onClick={() => setToastDismissed(true)}
                    className="text-emerald-700 hover:text-emerald-900 p-0.5 ml-1 rounded hover:bg-emerald-100/60"
                    aria-label="Dismiss message"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Welcome Heading Banner */}
          <div>
            <h1 className="text-2xl sm:text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
              Welcome back, {user.name}! 👋
            </h1>
            <p className="text-xs text-slate-500 mt-1">
              AI Support Ticket Management Platform - Dashboard Overview
            </p>
          </div>

          {/* Navigation Quick Pills Row (Preserved from Reference Design) */}
          <div className="flex items-center gap-2 flex-wrap pt-0.5">
            {renderLink(
              '/dashboard',
              <>
                <LayoutGrid className="w-3.5 h-3.5" />
                <span>Dashboard</span>
              </>,
              'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-blue-50/90 text-blue-700 border border-blue-200/80 hover:bg-blue-100 transition-colors cursor-pointer'
            )}

            {renderLink(
              '/tickets',
              <>
                <Ticket className="w-3.5 h-3.5" />
                <span>Tickets</span>
              </>,
              'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-pink-50/90 text-pink-700 border border-pink-200/80 hover:bg-pink-100 transition-colors cursor-pointer'
            )}

            {renderLink(
              '/tickets/new',
              <>
                <PlusCircle className="w-3.5 h-3.5" />
                <span>Submit Ticket</span>
              </>,
              'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-purple-50/90 text-purple-700 border border-purple-200/80 hover:bg-purple-100 transition-colors cursor-pointer'
            )}

            {renderLink(
              '/notifications',
              <>
                <Bell className="w-3.5 h-3.5" />
                <span>Notifications</span>
              </>,
              'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-amber-50/90 text-amber-800 border border-amber-200/80 hover:bg-amber-100 transition-colors cursor-pointer'
            )}

            {renderLink(
              '/change-password',
              <>
                <KeyRound className="w-3.5 h-3.5" />
                <span>Change Password</span>
              </>,
              'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-slate-100/90 text-slate-700 border border-slate-200/80 hover:bg-slate-200 transition-colors cursor-pointer'
            )}
          </div>

          {/* 3. TICKET STATISTICS CARDS (4 Pastel Tones: Lavender, Soft Yellow, Soft Blue, Soft Green) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Card 1: Total Tickets (Lavender) */}
            <div className="rounded-2xl p-5 bg-[#f4effe] border border-purple-200/70 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-purple-800 uppercase tracking-wider">
                  Total Tickets
                </span>
                <div className="p-2 rounded-xl bg-purple-200/60 text-purple-700">
                  <Ticket className="w-4 h-4" />
                </div>
              </div>
              <div className="mt-4">
                {isLoadingData ? (
                  <div className="h-9 w-16 bg-purple-200/60 rounded-lg animate-pulse" />
                ) : (
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {stats.total}
                  </span>
                )}
                <p className="text-[11px] text-purple-900/70 font-medium mt-1">
                  All support tickets created
                </p>
              </div>
              <div className="pt-3 border-t border-purple-200/50 mt-3 flex items-center justify-between">
                {renderLink(
                  '/tickets',
                  <>
                    <span>View all</span>
                    <ArrowRight className="w-3 h-3" />
                  </>,
                  'text-xs font-semibold text-purple-700 hover:text-purple-900 inline-flex items-center gap-1 transition-colors'
                )}
              </div>
            </div>

            {/* Card 2: Open Tickets (Soft Yellow) */}
            <div className="rounded-2xl p-5 bg-[#fffbeb] border border-amber-200/70 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-amber-800 uppercase tracking-wider">
                  Open Tickets
                </span>
                <div className="p-2 rounded-xl bg-amber-200/60 text-amber-700">
                  <Clock className="w-4 h-4" />
                </div>
              </div>
              <div className="mt-4">
                {isLoadingData ? (
                  <div className="h-9 w-16 bg-amber-200/60 rounded-lg animate-pulse" />
                ) : (
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {stats.open}
                  </span>
                )}
                <p className="text-[11px] text-amber-900/70 font-medium mt-1">
                  Awaiting agent review
                </p>
              </div>
              <div className="pt-3 border-t border-amber-200/50 mt-3 flex items-center justify-between">
                {renderLink(
                  '/tickets',
                  <>
                    <span>Filter open</span>
                    <ArrowRight className="w-3 h-3" />
                  </>,
                  'text-xs font-semibold text-amber-800 hover:text-amber-950 inline-flex items-center gap-1 transition-colors'
                )}
              </div>
            </div>

            {/* Card 3: In Progress (Soft Blue) */}
            <div className="rounded-2xl p-5 bg-[#eff6ff] border border-blue-200/70 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-blue-800 uppercase tracking-wider">
                  In Progress
                </span>
                <div className="p-2 rounded-xl bg-blue-200/60 text-blue-700">
                  <RefreshCw className="w-4 h-4" />
                </div>
              </div>
              <div className="mt-4">
                {isLoadingData ? (
                  <div className="h-9 w-16 bg-blue-200/60 rounded-lg animate-pulse" />
                ) : (
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {stats.inProgress}
                  </span>
                )}
                <p className="text-[11px] text-blue-900/70 font-medium mt-1">
                  Currently being investigated
                </p>
              </div>
              <div className="pt-3 border-t border-blue-200/50 mt-3 flex items-center justify-between">
                {renderLink(
                  '/tickets',
                  <>
                    <span>Filter active</span>
                    <ArrowRight className="w-3 h-3" />
                  </>,
                  'text-xs font-semibold text-blue-700 hover:text-blue-900 inline-flex items-center gap-1 transition-colors'
                )}
              </div>
            </div>

            {/* Card 4: Resolved (Soft Green) */}
            <div className="rounded-2xl p-5 bg-[#ecfdf5] border border-emerald-200/70 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-800 uppercase tracking-wider">
                  Resolved
                </span>
                <div className="p-2 rounded-xl bg-emerald-200/60 text-emerald-700">
                  <CheckCircle2 className="w-4 h-4" />
                </div>
              </div>
              <div className="mt-4">
                {isLoadingData ? (
                  <div className="h-9 w-16 bg-emerald-200/60 rounded-lg animate-pulse" />
                ) : (
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {stats.resolved}
                  </span>
                )}
                <p className="text-[11px] text-emerald-900/70 font-medium mt-1">
                  Successfully completed
                </p>
              </div>
              <div className="pt-3 border-t border-emerald-200/50 mt-3 flex items-center justify-between">
                {renderLink(
                  '/tickets',
                  <>
                    <span>View resolved</span>
                    <ArrowRight className="w-3 h-3" />
                  </>,
                  'text-xs font-semibold text-emerald-700 hover:text-emerald-900 inline-flex items-center gap-1 transition-colors'
                )}
              </div>
            </div>
          </div>

          {/* 4. THREE PASTEL FEATURE CARDS (Preserved for full visual alignment) */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Feature Card 1: My Support Tickets */}
            <div className="rounded-2xl p-5 bg-[#f4effe] border border-purple-100/90 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <div className="p-2.5 rounded-xl bg-purple-100 text-purple-600">
                    <Ticket className="w-4 h-4" />
                  </div>
                  {renderLink(
                    '/tickets',
                    <>
                      <span>View</span>
                      <ArrowRight className="w-3 h-3" />
                    </>,
                    'text-xs font-semibold text-slate-700 hover:text-blue-600 flex items-center gap-1 transition-colors'
                  )}
                </div>
                <h3 className="text-sm font-bold text-slate-900 mt-3.5">
                  {user.role === 'CUSTOMER'
                    ? 'My Support Tickets'
                    : user.role === 'AGENT'
                    ? 'Assigned Tickets'
                    : 'All Support Tickets'}
                </h3>
                <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                  Search, filter by status or priority, and ticket details.
                </p>
              </div>

              <div className="pt-4">
                {renderLink(
                  '/tickets',
                  'View',
                  'w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium text-xs rounded-xl shadow-xs transition-colors text-center block'
                )}
              </div>
            </div>

            {/* Feature Card 2: Submit New Ticket */}
            <div className="rounded-2xl p-5 bg-[#ecfdf5] border border-emerald-100/90 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <div className="p-2.5 rounded-xl bg-emerald-100 text-emerald-600">
                    <PlusCircle className="w-4 h-4" />
                  </div>
                  {renderLink(
                    '/tickets/new',
                    <>
                      <span>Create</span>
                      <ArrowRight className="w-3 h-3" />
                    </>,
                    'bg-blue-600 hover:bg-blue-700 text-white text-[11px] font-semibold px-2.5 py-1 rounded-lg flex items-center gap-1 transition-colors'
                  )}
                </div>
                <h3 className="text-sm font-bold text-slate-900 mt-3.5">Submit New Ticket</h3>
                <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                  Create a support ticket automatic categorization.
                </p>
              </div>

              <div className="pt-4">
                {renderLink(
                  '/tickets/new',
                  'Create',
                  'w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium text-xs rounded-xl shadow-xs transition-colors text-center block'
                )}
              </div>
            </div>

            {/* Feature Card 3: Notifications */}
            <div className="rounded-2xl p-5 bg-[#fffbeb] border border-amber-100/90 shadow-xs flex flex-col justify-between transition-all hover:shadow-sm">
              <div>
                <div className="flex items-center justify-between">
                  <div className="p-2.5 rounded-xl bg-amber-100 text-amber-600">
                    <Bell className="w-4 h-4" />
                  </div>
                  {renderLink(
                    '/notifications',
                    <>
                      <span>Inbox</span>
                      <ArrowRight className="w-3 h-3" />
                    </>,
                    'text-xs font-semibold text-slate-700 hover:text-blue-600 flex items-center gap-1 transition-colors'
                  )}
                </div>
                <h3 className="text-sm font-bold text-slate-900 mt-3.5">Notifications</h3>
                <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                  {liveUnreadCount > 0
                    ? `You have ${liveUnreadCount} unread notification${
                        liveUnreadCount === 1 ? '' : 's'
                      }.`
                    : 'All caught up with ticket updates and replies.'}
                </p>
              </div>

              <div className="pt-4">
                {renderLink(
                  '/notifications',
                  'Inbox',
                  'w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium text-xs rounded-xl shadow-xs transition-colors text-center block'
                )}
              </div>
            </div>
          </div>

          {/* 5. TWO-COLUMN SECTION: Recent Tickets (Left) + Recent Notifications & Session Info (Right) */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* LEFT COLUMN: Recent Tickets Table (7 cols on lg, 8 on xl) */}
            <div className="lg:col-span-7 xl:col-span-8 space-y-4">
              <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 sm:p-6">
                <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Recent Tickets</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Your latest support inquiries and real-time statuses
                    </p>
                  </div>
                  {renderLink(
                    '/tickets',
                    <>
                      <span>View All</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </>,
                    'text-xs font-semibold text-blue-600 hover:text-blue-700 inline-flex items-center gap-1 transition-colors'
                  )}
                </div>

                {/* Table or Empty/Loading State */}
                <div className="mt-4">
                  {isLoadingData ? (
                    <div className="py-8 text-center space-y-2">
                      <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto" />
                      <p className="text-xs text-slate-400">Loading tickets...</p>
                    </div>
                  ) : recentTickets.length === 0 ? (
                    <div className="py-10 text-center flex flex-col items-center justify-center">
                      <div className="p-3 rounded-2xl bg-slate-50 border border-slate-100 text-slate-400 mb-3">
                        <Inbox className="w-6 h-6" />
                      </div>
                      <p className="text-sm font-bold text-slate-700">No tickets found</p>
                      <p className="text-xs text-slate-400 max-w-xs mt-1">
                        You have not submitted any support tickets yet.
                      </p>
                      <div className="mt-4">
                        {renderLink(
                          '/tickets/new',
                          <>
                            <PlusCircle className="w-3.5 h-3.5" />
                            <span>Create Ticket</span>
                          </>,
                          'px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs inline-flex items-center gap-1.5 shadow-xs transition-colors'
                        )}
                      </div>
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-xs">
                        <thead>
                          <tr className="border-b border-slate-100 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                            <th className="pb-3 font-semibold">Ticket ID</th>
                            <th className="pb-3 font-semibold">Title</th>
                            <th className="pb-3 font-semibold">Status</th>
                            <th className="pb-3 font-semibold">Priority</th>
                            <th className="pb-3 font-semibold hidden sm:table-cell">Category</th>
                            <th className="pb-3 font-semibold hidden md:table-cell">Created</th>
                            <th className="pb-3 font-semibold text-right">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 text-slate-700">
                          {recentTickets.map((ticket) => (
                            <tr key={ticket.id} className="hover:bg-slate-50/70 transition-colors">
                              <td className="py-3 font-bold text-slate-900 font-mono">
                                #{ticket.id}
                              </td>
                              <td className="py-3 max-w-[180px] sm:max-w-[220px] truncate font-medium text-slate-900">
                                {ticket.title}
                              </td>
                              <td className="py-3">
                                <StatusBadge status={ticket.status} />
                              </td>
                              <td className="py-3">
                                <PriorityBadge priority={ticket.priority} />
                              </td>
                              <td className="py-3 hidden sm:table-cell">
                                <CategoryBadge category={ticket.category} />
                              </td>
                              <td className="py-3 hidden md:table-cell text-slate-400 text-[11px]">
                                {formatTimeAgo(ticket.createdAt)}
                              </td>
                              <td className="py-3 text-right">
                                {renderLink(
                                  `/tickets/${ticket.id}`,
                                  <>
                                    <span>View</span>
                                    <ArrowRight className="w-3 h-3" />
                                  </>,
                                  'text-xs font-semibold text-blue-600 hover:text-blue-800 inline-flex items-center gap-1 transition-colors'
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* RIGHT COLUMN: Recent Notifications & Session Info (5 cols on lg, 4 on xl) */}
            <div className="lg:col-span-5 xl:col-span-4 space-y-6">
              {/* Recent Notifications Panel */}
              <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-slate-900">Recent Notifications</h3>
                    {liveUnreadCount > 0 && (
                      <span className="px-1.5 py-0.2 rounded-full bg-emerald-100 text-emerald-700 font-bold text-[10px]">
                        {liveUnreadCount} new
                      </span>
                    )}
                  </div>
                  {renderLink(
                    '/notifications',
                    'View all',
                    'text-xs font-semibold text-blue-600 hover:text-blue-700 transition-colors'
                  )}
                </div>

                <div className="mt-3 divide-y divide-slate-100">
                  {recentNotifications.length === 0 ? (
                    <div className="py-6 text-center text-slate-400 text-xs">
                      <Inbox className="w-5 h-5 mx-auto mb-1.5 opacity-60" />
                      <span>All caught up! No recent notifications.</span>
                    </div>
                  ) : (
                    recentNotifications.slice(0, 4).map((notif) => (
                      <div
                        key={notif.id}
                        onClick={() => handleNotificationClick(notif)}
                        className={`py-3 flex items-start gap-3 transition-colors rounded-xl px-2.5 -mx-2.5 ${
                          isPreview ? '' : 'cursor-pointer hover:bg-slate-50/80'
                        }`}
                        role={isPreview ? undefined : 'button'}
                        tabIndex={isPreview ? undefined : 0}
                        onKeyDown={
                          isPreview
                            ? undefined
                            : (e) => {
                                if (e.key === 'Enter' || e.key === ' ') {
                                  e.preventDefault();
                                  handleNotificationClick(notif);
                                }
                              }
                        }
                      >
                        <div
                          className={`p-1.5 rounded-lg shrink-0 mt-0.5 ${
                            notif.read
                              ? 'bg-slate-100 text-slate-400'
                              : 'bg-emerald-50 text-emerald-600'
                          }`}
                        >
                          <Bell className="w-3.5 h-3.5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p
                            className={`text-xs leading-snug line-clamp-2 ${
                              notif.read
                                ? 'text-slate-600 font-normal'
                                : 'text-slate-900 font-semibold'
                            }`}
                          >
                            {notif.message}
                          </p>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-[10px] text-slate-400">
                              {formatTimeAgo(notif.createdAt)}
                            </span>
                            {notif.ticketId && (
                              <span className="text-[10px] text-blue-600 font-medium">
                                • Ticket #{notif.ticketId}
                              </span>
                            )}
                          </div>
                        </div>
                        {!notif.read && (
                          <span
                            className="w-2 h-2 rounded-full bg-emerald-500 mt-1.5 shrink-0"
                            title="Unread"
                          />
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Session & Role Information Panel (From Reference Screenshot) */}
              <div className="rounded-2xl p-5 bg-white border border-slate-200/80 shadow-xs space-y-3.5">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">
                      Session & Role Information
                    </h3>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      Authenticated identity verified by the Spring Boot backend
                    </p>
                  </div>
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border border-emerald-300 bg-emerald-50 text-emerald-700">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    {user.role}
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                  {/* Tile 1: Full Name */}
                  <div className="p-3.5 rounded-xl bg-[#f4effe]/70 border border-purple-100 flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-purple-100 text-purple-600">
                      <UserIcon className="w-4 h-4" />
                    </div>
                    <div>
                      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                        FULL NAME
                      </p>
                      <p className="text-xs font-bold text-slate-900 mt-0.5">{user.name}</p>
                    </div>
                  </div>

                  {/* Tile 2: Email Address */}
                  <div className="p-3.5 rounded-xl bg-[#fffbeb]/70 border border-amber-100 flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-amber-100 text-amber-600">
                      <Mail className="w-4 h-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                        EMAIL ADDRESS
                      </p>
                      <p className="text-xs font-bold text-slate-900 mt-0.5 truncate">
                        {user.email}
                      </p>
                    </div>
                  </div>
                </div>

                {/* Active Session Teal Banner */}
                <div className="p-3 rounded-xl bg-emerald-50/80 border border-emerald-200/80 flex items-center gap-2.5 text-xs text-emerald-800">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>
                    Session active with Role <strong>{user.role}</strong>
                    {user.userId ? ` (User ID: #${user.userId})` : ''}. All API calls
                    automatically scoped by the backend.
                  </span>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
