import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import {
  getMyNotificationsApi,
  getUnreadCountApi,
  markNotificationAsReadApi,
} from '@/lib/api/notifications';
import type { NotificationResponse } from '@/types/notification.types';
import { Bell, Check, ExternalLink, Loader2, Inbox } from 'lucide-react';
import { formatDistanceToNow, parseISO } from 'date-fns';
import { toast } from 'sonner';

export function NotificationBell() {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isOpen, setIsOpen] = useState<boolean>(false);

  // Fetch unread count
  const fetchUnreadCount = useCallback(async () => {
    try {
      const data = await getUnreadCountApi();
      setUnreadCount(data.unreadCount);
    } catch {
      // Silently ignore if unauthenticated or network failure
    }
  }, []);

  // Fetch recent notifications when dropdown opens
  const fetchRecent = useCallback(async () => {
    setIsLoading(true);
    try {
      const pageData = await getMyNotificationsApi({ page: 0, size: 5 });
      setNotifications(pageData.content);
    } catch {
      // Silently fail in dropdown preview
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUnreadCount();
    // Poll unread count every 30 seconds
    const interval = setInterval(fetchUnreadCount, 30000);
    return () => clearInterval(interval);
  }, [fetchUnreadCount]);

  const handleOpenChange = (open: boolean) => {
    setIsOpen(open);
    if (open) {
      fetchRecent();
      fetchUnreadCount();
    }
  };

  const handleMarkAsRead = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await markNotificationAsReadApi(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
      toast.success('Notification marked as read.');
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      toast.error(apiErr.message || 'Failed to mark notification as read.');
    }
  };

  const handleItemClick = (notification: NotificationResponse) => {
    if (!notification.read) {
      markNotificationAsReadApi(notification.id)
        .then(() => {
          setUnreadCount((prev) => Math.max(0, prev - 1));
        })
        .catch(() => {});
    }
    setIsOpen(false);
    if (notification.ticketId) {
      navigate(`/tickets/${notification.ticketId}`);
    } else {
      navigate('/notifications');
    }
  };

  return (
    <DropdownMenu.Root open={isOpen} onOpenChange={handleOpenChange}>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          aria-label="View notifications"
          className="relative p-2 rounded-lg text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
        >
          <Bell className="w-4 h-4" />
          {unreadCount > 0 && (
            <span className="absolute top-1 right-1 flex items-center justify-center min-w-4 h-4 px-1 rounded-full text-[10px] font-bold bg-rose-600 text-white shadow-xs animate-in zoom-in-75">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>
      </DropdownMenu.Trigger>

      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={8}
          className="w-80 sm:w-96 rounded-xl bg-white p-0 shadow-xl border border-slate-200 z-50 animate-in fade-in zoom-in-95 duration-150 focus:outline-none"
        >
          {/* Header */}
          <div className="flex items-center justify-between p-3.5 border-b border-slate-100">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-slate-900">Notifications</span>
              {unreadCount > 0 && (
                <span className="px-1.5 py-0.2 rounded-full text-[10px] font-semibold bg-rose-50 text-rose-600 border border-rose-200/60">
                  {unreadCount} unread
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                navigate('/notifications');
              }}
              className="text-[11px] font-medium text-indigo-600 hover:text-indigo-800 transition-colors flex items-center gap-1"
            >
              <span>View All</span>
              <ExternalLink className="w-3 h-3" />
            </button>
          </div>

          {/* List Content */}
          <div className="max-h-80 overflow-y-auto divide-y divide-slate-100">
            {isLoading ? (
              <div className="p-8 flex items-center justify-center text-slate-400 gap-2 text-xs">
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Loading notifications...</span>
              </div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center text-slate-500 space-y-1">
                <Inbox className="w-7 h-7 mx-auto text-slate-300 stroke-1" />
                <p className="text-xs font-medium text-slate-700">No notifications</p>
                <p className="text-[11px] text-slate-400">You are all caught up!</p>
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => handleItemClick(n)}
                  className={`p-3 text-left transition-colors cursor-pointer flex items-start gap-2.5 hover:bg-slate-50 ${
                    !n.read ? 'bg-indigo-50/30' : ''
                  }`}
                >
                  <div className="pt-0.5">
                    <span
                      className={`inline-block w-2 h-2 rounded-full ${
                        !n.read ? 'bg-indigo-600' : 'bg-transparent'
                      }`}
                    />
                  </div>

                  <div className="flex-1 min-w-0 space-y-0.5">
                    <p className="text-xs text-slate-800 leading-snug break-words">
                      {n.message}
                    </p>
                    <div className="flex items-center gap-2 text-[10px] text-slate-400">
                      <span>{n.type}</span>
                      <span>•</span>
                      <span>
                        {formatDistanceToNow(parseISO(n.createdAt), { addSuffix: true })}
                      </span>
                    </div>
                  </div>

                  {!n.read && (
                    <button
                      type="button"
                      onClick={(e) => handleMarkAsRead(n.id, e)}
                      title="Mark as read"
                      className="p-1 rounded text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 transition-colors shrink-0"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="p-2 border-t border-slate-100 bg-slate-50/60 rounded-b-xl text-center">
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                navigate('/notifications');
              }}
              className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 py-1 px-3 rounded transition-colors w-full"
            >
              Go to Notifications Page →
            </button>
          </div>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
