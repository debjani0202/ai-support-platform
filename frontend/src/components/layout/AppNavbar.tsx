import { Link, NavLink, useNavigate } from 'react-router-dom';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { useAuth } from '@/hooks/useAuth';
import {
  PlusCircle,
  LayoutDashboard,
  Ticket as TicketIcon,
  LogOut,
  Bell,
  KeyRound,
  ChevronDown,
  User,
  UserCheck,
} from 'lucide-react';
import { BrandLogo } from '@/components/common/BrandLogo';
import { NotificationBell } from '@/components/notifications/NotificationBell';
import { toast } from 'sonner';

export function AppNavbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    toast.info('You have been logged out.');
    navigate('/login');
  };

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
      isActive
        ? 'bg-blue-50 text-blue-700 font-semibold'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
    }`;

  const getRoleBadgeStyle = (role?: string) => {
    switch (role) {
      case 'ADMIN':
        return 'bg-purple-50 text-purple-700 border-purple-200/80';
      case 'AGENT':
        return 'bg-blue-50 text-blue-700 border-blue-200/80';
      default:
        return 'bg-emerald-50 text-emerald-700 border-emerald-300';
    }
  };

  const ticketsLabel =
    user?.role === 'AGENT'
      ? 'Assigned Tickets'
      : user?.role === 'ADMIN'
      ? 'All Tickets'
      : 'My Tickets';

  const displayName = user?.name?.trim() || 'User';
  const userInitial = displayName.charAt(0).toUpperCase();

  return (
    <header className="sticky top-0 z-40 w-full border-b border-slate-100 bg-white/95 backdrop-blur-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-14 flex items-center justify-between">
        {/* Brand & Left Navigation */}
        <div className="flex items-center gap-6">
          <NavLink to="/dashboard" className="flex items-center gap-2.5">
            <BrandLogo size={24} />
            <span className="font-bold text-slate-900 tracking-tight text-sm">
              AI Support Platform
            </span>
          </NavLink>

          <nav className="flex items-center gap-1">
            <NavLink to="/dashboard" className={navLinkClass}>
              <LayoutDashboard className="w-4 h-4" />
              <span>Dashboard</span>
            </NavLink>
            <NavLink to="/tickets" end className={navLinkClass}>
              <TicketIcon className="w-4 h-4" />
              <span>{ticketsLabel}</span>
            </NavLink>
            {user?.role !== 'ADMIN' && (
              <NavLink to="/tickets/new" className={navLinkClass}>
                <PlusCircle className="w-4 h-4 text-indigo-600" />
                <span>Submit Ticket</span>
              </NavLink>
            )}
            <NavLink to="/notifications" className={navLinkClass}>
              <Bell className="w-4 h-4" />
              <span>Notifications</span>
            </NavLink>
            {user?.role === 'ADMIN' && (
              <NavLink to="/admin/agent-requests" className={navLinkClass}>
                <UserCheck className="w-4 h-4 text-indigo-600" />
                <span>Agent Requests</span>
              </NavLink>
            )}
          </nav>
        </div>

        {/* User Identity, Notification Bell & Profile Dropdown */}
        <div className="flex items-center gap-2 sm:gap-3">
          <NotificationBell />

          <DropdownMenu.Root>
            <DropdownMenu.Trigger asChild>
              <button
                type="button"
                aria-label="Open user menu"
                className="flex items-center gap-2 p-1 rounded-full hover:bg-slate-100 text-slate-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 cursor-pointer"
              >
                <div className="w-8 h-8 rounded-full bg-purple-600 text-white font-bold text-xs flex items-center justify-center shadow-xs">
                  {userInitial}
                </div>
                <div className="hidden sm:flex flex-col items-start text-left">
                  <span className="text-xs font-semibold text-slate-800 max-w-[130px] truncate leading-tight">
                    {displayName}
                  </span>
                  <span
                    className={`inline-flex items-center gap-1 mt-0.5 px-1.5 py-0.2 rounded-full text-[9px] font-semibold border ${getRoleBadgeStyle(
                      user?.role
                    )}`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    {user?.role || 'CUSTOMER'}
                  </span>
                </div>
                <ChevronDown className="hidden sm:inline w-3.5 h-3.5 text-slate-500 ml-0.5" />
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
                    {user?.email || 'User Account'}
                  </p>
                  <span
                    className={`mt-1.5 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold border ${getRoleBadgeStyle(
                      user?.role
                    )}`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    {user?.role || 'CUSTOMER'}
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
                    onClick={handleLogout}
                    className="w-full flex items-center gap-2 px-3 py-2 text-xs font-medium text-rose-600 rounded-lg hover:bg-rose-50 cursor-pointer outline-none transition-colors"
                  >
                    <LogOut className="w-3.5 h-3.5 text-rose-500" />
                    <span>Sign Out</span>
                  </button>
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        </div>
      </div>
    </header>
  );
}
