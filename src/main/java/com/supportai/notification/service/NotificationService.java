package com.supportai.notification.service;

import com.supportai.exception.ResourceNotFoundException;
import com.supportai.notification.dto.NotificationResponse;
import com.supportai.notification.entity.Notification;
import com.supportai.notification.repository.NotificationRepository;
import com.supportai.ticket.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(
            Long currentUserId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        validatePagination(page, size);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Notification> notifications = unreadOnly
                ? notificationRepository
                        .findByRecipientIdAndReadFalseOrderByCreatedAtDesc(
                                currentUserId,
                                pageable
                        )
                : notificationRepository
                        .findByRecipientIdOrderByCreatedAtDesc(
                                currentUserId,
                                pageable
                        );

        return new PageResponse<>(
                notifications.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                notifications.isFirst(),
                notifications.isLast()
        );
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long currentUserId) {
        return notificationRepository
                .countByRecipientIdAndReadFalse(currentUserId);
    }

    public void markAsRead(Long notificationId, Long currentUserId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with ID: " + notificationId
                ));

        if (!notification.getRecipient().getId().equals(currentUserId)) {
            throw new ResourceNotFoundException(
                    "Notification not found with ID: " + notificationId
            );
        }

        notification.markAsRead();
    }

    public void markAllAsRead(Long currentUserId) {
        notificationRepository.markAllAsReadByRecipientId(currentUserId);
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTicket() != null
                        ? notification.getTicket().getId()
                        : null,
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    /**
     * Creates a notification for a recipient.
     */
    public void createNotification(
            com.supportai.user.entity.User recipient,
            com.supportai.ticket.entity.Ticket ticket,
            String type,
            String message) {

        Notification notification = new Notification(
                recipient,
                ticket,
                type,
                message
        );

        notificationRepository.save(notification);
    }
}
