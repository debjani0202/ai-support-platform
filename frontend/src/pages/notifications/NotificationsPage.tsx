import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  getMyNotificationsApi,
  getUnreadCountApi,
  markNotificationAsReadApi,
} from '@/lib/api/notifications';
import type { NotificationResponse } from '@/types/notification.types';
import type { SpringPage } from '@/types/api.types';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import {
  Bell,
  Check,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Inbox,
  AlertCircle,
  Filter,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';
import { toast } from 'sonner';

export function NotificationsPage() {
  const navigate = useNavigate();
  const [pageData, setPageData] = useState<SpringPage<NotificationResponse> | null>(null);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [unreadOnly, setUnreadOnly] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchUnreadCount = useCallback(async () => {
    try {
      const data = await getUnreadCountApi();
      setUnreadCount(data.unreadCount);
    } catch {
      // Ignored
    }
  }, []);

  const fetchNotifications = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getMyNotificationsApi({
        page,
        size: 15,
        unreadOnly,
      });
      setPageData(data);
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      setError(apiErr.message || 'Failed to load notifications.');
    } finally {
      setIsLoading(false);
    }
  }, [page, unreadOnly]);

  useEffect(() => {
    fetchNotifications();
    fetchUnreadCount();
  }, [fetchNotifications, fetchUnreadCount]);

  const handleMarkAsRead = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await markNotificationAsReadApi(id);
      toast.success('Notification marked as read.');
      setPageData((prev) => {
        if (!prev) return null;
        return {
          ...prev,
          content: prev.content.map((n) =>
            n.id === id ? { ...n, read: true } : n
          ),
        };
      });
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      toast.error(apiErr.message || 'Failed to mark notification as read.');
    }
  };

  const handleNotificationClick = (notification: NotificationResponse) => {
    if (!notification.read) {
      markNotificationAsReadApi(notification.id)
        .then(() => {
          setUnreadCount((prev) => Math.max(0, prev - 1));
        })
        .catch(() => {});
    }
    if (notification.ticketId) {
      navigate(`/tickets/${notification.ticketId}`);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppNavbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                Notifications
              </h1>
              {unreadCount > 0 && (
                <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-600 border border-rose-200/80">
                  {unreadCount} unread
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Real-time system updates on ticket assignments, status changes, and replies.
            </p>
          </div>

          {/* Filter Pills */}
          <div className="flex items-center gap-1.5 p-1 bg-white border border-slate-200/80 rounded-lg self-start sm:self-auto shadow-xs">
            <button
              type="button"
              onClick={() => {
                setUnreadOnly(false);
                setPage(0);
              }}
              className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-colors ${
                !unreadOnly
                  ? 'bg-indigo-600 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              All Notifications
            </button>
            <button
              type="button"
              onClick={() => {
                setUnreadOnly(true);
                setPage(0);
              }}
              className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-colors flex items-center gap-1 ${
                unreadOnly
                  ? 'bg-indigo-600 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Filter className="w-3 h-3" />
              <span>Unread Only</span>
            </button>
          </div>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
            <Button variant="outline" size="sm" onClick={fetchNotifications}>
              Retry
            </Button>
          </div>
        )}

        {/* Notifications List Card */}
        <Card>
          <CardHeader className="pb-3 border-b border-slate-100">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2">
                <Bell className="w-4 h-4 text-indigo-600" />
                <span>Inbox Activity</span>
              </CardTitle>
              {pageData && (
                <span className="text-xs text-slate-400 font-mono">
                  {pageData.totalElements} total
                </span>
              )}
            </div>
          </CardHeader>

          <CardContent className="p-0">
            {/* Loading Skeletons */}
            {isLoading && (
              <div className="divide-y divide-slate-100 p-4 space-y-4 animate-pulse">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="pt-3 first:pt-0 space-y-2">
                    <div className="h-4 w-1/3 bg-slate-200 rounded" />
                    <div className="h-3 w-3/4 bg-slate-200 rounded" />
                  </div>
                ))}
              </div>
            )}

            {/* Empty State */}
            {!isLoading && (!pageData || pageData.content.length === 0) && (
              <div className="p-12 text-center text-slate-500 space-y-3">
                <Inbox className="w-10 h-10 mx-auto text-slate-300 stroke-1" />
                <div>
                  <p className="text-sm font-semibold text-slate-800">
                    {unreadOnly ? 'No unread notifications' : 'No notifications found'}
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {unreadOnly
                      ? 'You have reviewed all your recent activity.'
                      : 'Activity updates for your tickets will appear here.'}
                  </p>
                </div>
                {unreadOnly && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setUnreadOnly(false);
                      setPage(0);
                    }}
                  >
                    View All Notifications
                  </Button>
                )}
              </div>
            )}

            {/* Notification Rows */}
            {!isLoading && pageData && pageData.content.length > 0 && (
              <div className="divide-y divide-slate-100">
                {pageData.content.map((n) => (
                  <div
                    key={n.id}
                    onClick={() => handleNotificationClick(n)}
                    className={`p-4 flex items-start gap-4 transition-colors cursor-pointer hover:bg-slate-50/80 ${
                      !n.read ? 'bg-indigo-50/20' : 'bg-white'
                    }`}
                  >
                    {/* Read indicator dot */}
                    <div className="pt-1">
                      <span
                        className={`inline-block w-2.5 h-2.5 rounded-full ${
                          !n.read ? 'bg-indigo-600 ring-4 ring-indigo-100' : 'bg-slate-200'
                        }`}
                      />
                    </div>

                    <div className="flex-1 min-w-0 space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-700 border border-slate-200/60 font-mono">
                          {n.type}
                        </span>

                        {n.ticketId && (
                          <Link
                            to={`/tickets/${n.ticketId}`}
                            onClick={(e) => e.stopPropagation()}
                            className="inline-flex items-center gap-1 text-[11px] font-semibold text-indigo-600 hover:text-indigo-800"
                          >
                            <span>Ticket #{n.ticketId}</span>
                            <ExternalLink className="w-3 h-3" />
                          </Link>
                        )}

                        <span className="text-[11px] text-slate-400">
                          {format(parseISO(n.createdAt), 'PPp')}
                        </span>
                      </div>

                      <p className="text-sm text-slate-900 leading-snug break-words">
                        {n.message}
                      </p>
                    </div>

                    {/* Mark as read button */}
                    {!n.read && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => handleMarkAsRead(n.id, e)}
                        className="text-xs text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 shrink-0"
                        title="Mark as read"
                      >
                        <Check className="w-3.5 h-3.5 mr-1" />
                        <span>Mark Read</span>
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Pagination Bar */}
        {pageData && pageData.totalPages > 1 && (
          <div className="flex items-center justify-between text-xs text-slate-500 px-1">
            <span>
              Page {pageData.number + 1} of {pageData.totalPages} ({pageData.totalElements} total notifications)
            </span>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                disabled={pageData.first || isLoading}
              >
                <ChevronLeft className="w-3.5 h-3.5 mr-1" />
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((prev) => prev + 1)}
                disabled={pageData.last || isLoading}
              >
                Next
                <ChevronRight className="w-3.5 h-3.5 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
