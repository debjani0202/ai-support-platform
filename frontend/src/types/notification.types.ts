/**
 * Matches com.supportai.notification.dto.NotificationResponse
 */
export interface NotificationResponse {
  id: number;
  ticketId: number;
  type: string;
  message: string;
  read: boolean;
  createdAt: string; // ISO-8601 string from backend Instant
}

/**
 * Matches Map<String, Long> returned by GET /api/notifications/unread-count
 */
export interface UnreadCountResponse {
  unreadCount: number;
}

/**
 * Query parameters for GET /api/notifications
 * Matches com.supportai.notification.controller.NotificationController#getMyNotifications
 */
export interface NotificationQueryParams {
  page?: number;
  size?: number;
  unreadOnly?: boolean;
}
