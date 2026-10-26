import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import {
  ShieldCheck,
  PlusCircle,
  LayoutDashboard,
  Ticket as TicketIcon,
  LogOut,
  Shield,
  Bell,
  KeyRound,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
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
        ? 'bg-indigo-50 text-indigo-700 font-semibold'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
    }`;

  const getRoleBadgeStyle = (role?: string) => {
    switch (role) {
      case 'ADMIN':
        return 'bg-purple-50 text-purple-700 border-purple-200/80';
      case 'AGENT':
        return 'bg-blue-50 text-blue-700 border-blue-200/80';
      default:
        return 'bg-emerald-50 text-emerald-700 border-emerald-200/80';
    }
  };

  return (
    <header className="sticky top-0 z-40 w-full border-b border-slate-200/80 bg-white/95 backdrop-blur-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-15 flex items-center justify-between">
        {/* Brand & Left Navigation */}
        <div className="flex items-center gap-6">
          <NavLink to="/dashboard" className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600 border border-indigo-100/60 shadow-xs">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <span className="font-bold text-slate-900 tracking-tight text-sm hidden sm:inline">
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
              <span>Tickets</span>
            </NavLink>
            <NavLink to="/tickets/new" className={navLinkClass}>
              <PlusCircle className="w-4 h-4 text-indigo-600" />
              <span>Submit Ticket</span>
            </NavLink>
            <NavLink to="/notifications" className={navLinkClass}>
              <Bell className="w-4 h-4" />
              <span>Notifications</span>
            </NavLink>
            <NavLink to="/change-password" className={navLinkClass}>
              <KeyRound className="w-4 h-4" />
              <span>Change Password</span>
            </NavLink>
          </nav>
        </div>

        {/* User Identity, Notification Bell & Logout */}
        <div className="flex items-center gap-2 sm:gap-3">
          <NotificationBell />
          <div className="hidden md:flex flex-col items-end text-right">
            <span className="text-xs font-semibold text-slate-900 leading-tight">
              {user?.name}
            </span>
            <span
              className={`inline-flex items-center gap-1 mt-0.5 px-2 py-0.2 rounded-full text-[10px] font-semibold border ${getRoleBadgeStyle(
                user?.role
              )}`}
            >
              <Shield className="w-2.5 h-2.5" />
              {user?.role}
            </span>
          </div>

          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            className="text-slate-500 hover:text-rose-600 hover:bg-rose-50"
            title="Log out of application"
          >
            <LogOut className="w-4 h-4 sm:mr-1.5" />
            <span className="hidden sm:inline">Sign Out</span>
          </Button>
        </div>
      </div>
    </header>
  );
}
