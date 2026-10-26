import { apiClient } from './client';
import type { PageResponse } from '@/types/api.types';
import type {
  NotificationResponse,
  UnreadCountResponse,
  NotificationQueryParams,
} from '@/types/notification.types';

/**
 * Fetches paginated notifications for the authenticated user.
 * Matches GET /api/notifications
 */
export async function getMyNotificationsApi(
  params: NotificationQueryParams = {}
): Promise<PageResponse<NotificationResponse>> {
  const cleanParams: Record<string, unknown> = {};
  if (params.page !== undefined) cleanParams.page = params.page;
  if (params.size !== undefined) cleanParams.size = params.size;
  if (params.unreadOnly !== undefined) cleanParams.unreadOnly = params.unreadOnly;

  const response = await apiClient.get<PageResponse<NotificationResponse>>(
    '/notifications',
    { params: cleanParams }
  );
  return response.data;
}

/**
 * Fetches the count of unread notifications for the authenticated user.
 * Matches GET /api/notifications/unread-count
 */
export async function getUnreadCountApi(): Promise<UnreadCountResponse> {
  const response = await apiClient.get<UnreadCountResponse>(
    '/notifications/unread-count'
  );
  return response.data;
}

/**
 * Marks a specific notification as read.
 * Matches PATCH /api/notifications/{id}/read
 */
export async function markNotificationAsReadApi(id: number): Promise<void> {
  await apiClient.patch(`/notifications/${id}/read`);
}

/**
 * Marks all notifications as read for the authenticated user.
 * Matches PATCH /api/notifications/read-all
 */
export async function markAllNotificationsAsReadApi(): Promise<void> {
  await apiClient.patch('/notifications/read-all');
}
