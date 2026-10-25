import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { getUnreadCountApi } from '@/lib/api/notifications';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { User, Mail, Shield, CheckCircle2, Ticket, PlusCircle, Bell, ArrowRight } from 'lucide-react';

export function DashboardPlaceholder() {
  const { user } = useAuth();
  const [unreadCount, setUnreadCount] = useState<number>(0);

  useEffect(() => {
    getUnreadCountApi()
      .then((data) => setUnreadCount(data.unreadCount))
      .catch(() => {});
  }, []);

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
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppNavbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Welcome back, {user?.name}!
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            AI Support Ticket Management Platform • Dashboard Overview
          </p>
        </div>

        {/* Quick Action Navigation Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <Card className="hover:border-indigo-300 transition-colors">
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <div className="p-2.5 rounded-lg bg-indigo-50 text-indigo-600">
                  <Ticket className="w-5 h-5" />
                </div>
                <Link to="/tickets">
                  <Button variant="ghost" size="sm">
                    View <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </Link>
              </div>
              <CardTitle className="text-base pt-2">
                {user?.role === 'CUSTOMER'
                  ? 'My Support Tickets'
                  : user?.role === 'AGENT'
                  ? 'Assigned Tickets'
                  : 'All Support Tickets'}
              </CardTitle>
              <CardDescription>
                Search, filter by status or priority, and inspect ticket details.
              </CardDescription>
            </CardHeader>
          </Card>

          <Card className="hover:border-indigo-300 transition-colors">
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <div className="p-2.5 rounded-lg bg-emerald-50 text-emerald-600">
                  <PlusCircle className="w-5 h-5" />
                </div>
                <Link to="/tickets/new">
                  <Button variant="primary" size="sm">
                    Create <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </Link>
              </div>
              <CardTitle className="text-base pt-2">Submit New Ticket</CardTitle>
              <CardDescription>
                Create a support ticket with AI-assisted automatic categorization.
              </CardDescription>
            </CardHeader>
          </Card>

          <Card className="hover:border-indigo-300 transition-colors">
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <div className="p-2.5 rounded-lg bg-amber-50 text-amber-600 relative">
                  <Bell className="w-5 h-5" />
                  {unreadCount > 0 && (
                    <span className="absolute -top-1 -right-1 flex items-center justify-center min-w-4 h-4 px-1 rounded-full text-[10px] font-bold bg-rose-600 text-white">
                      {unreadCount}
                    </span>
                  )}
                </div>
                <Link to="/notifications">
                  <Button variant="ghost" size="sm">
                    Inbox <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </Link>
              </div>
              <CardTitle className="text-base pt-2">Notifications</CardTitle>
              <CardDescription>
                {unreadCount > 0
                  ? `You have ${unreadCount} unread notification${unreadCount === 1 ? '' : 's'}.`
                  : 'All caught up with ticket updates and replies.'}
              </CardDescription>
            </CardHeader>
          </Card>
        </div>

        {/* Authenticated User Profile Summary Card */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Session & Role Information</CardTitle>
              <span
                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${getRoleBadgeStyle(
                  user?.role
                )}`}
              >
                <Shield className="w-3 h-3" />
                {user?.role || 'CUSTOMER'}
              </span>
            </div>
            <CardDescription>
              Authenticated identity verified by the Spring Boot backend
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 pt-2">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="p-3.5 rounded-lg bg-slate-50 border border-slate-200/60 flex items-start gap-3">
                <div className="p-2 rounded-md bg-white border border-slate-200/60 text-slate-600">
                  <User className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Full Name</p>
                  <p className="text-sm font-semibold text-slate-900">{user?.name || 'N/A'}</p>
                </div>
              </div>

              <div className="p-3.5 rounded-lg bg-slate-50 border border-slate-200/60 flex items-start gap-3">
                <div className="p-2 rounded-md bg-white border border-slate-200/60 text-slate-600">
                  <Mail className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Email Address</p>
                  <p className="text-sm font-semibold text-slate-900">{user?.email || 'N/A'}</p>
                </div>
              </div>
            </div>

            <div className="p-3.5 rounded-lg bg-emerald-50/50 border border-emerald-200/60 flex items-center gap-2.5 text-xs text-emerald-800">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>
                Session active with Role <strong>{user?.role}</strong> (User ID: #{user?.userId}). All API calls automatically scoped by the backend.
              </span>
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
